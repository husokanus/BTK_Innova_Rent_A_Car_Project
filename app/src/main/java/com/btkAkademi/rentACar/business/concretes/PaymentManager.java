package com.btkAkademi.rentACar.business.concretes;

import java.time.LocalDate;
import java.time.Period;

import org.springframework.stereotype.Service;

import com.btkAkademi.rentACar.business.abstracts.CreditCardInfoService;
import com.btkAkademi.rentACar.business.abstracts.InvoiceService;
import com.btkAkademi.rentACar.business.abstracts.PaymentService;
import com.btkAkademi.rentACar.business.abstracts.PosSystemService;
import com.btkAkademi.rentACar.business.abstracts.PromotionCodeService;
import com.btkAkademi.rentACar.business.abstracts.RentalService;
import com.btkAkademi.rentACar.business.dtos.InvoiceListDtoProj;
import com.btkAkademi.rentACar.business.requests.creditCardInfoRequest.CreateCreditCardInfoRequest;
import com.btkAkademi.rentACar.business.requests.paymentRequests.CreatePaymentRequest;
import com.btkAkademi.rentACar.core.utilities.business.BusinessRules;
import com.btkAkademi.rentACar.core.utilities.constants.Messages;
import com.btkAkademi.rentACar.core.utilities.mapping.ModelMapperService;
import com.btkAkademi.rentACar.core.utilities.results.DataResult;
import com.btkAkademi.rentACar.core.utilities.results.ErrorDataResult;
import com.btkAkademi.rentACar.core.utilities.results.ErrorResult;
import com.btkAkademi.rentACar.core.utilities.results.Result;
import com.btkAkademi.rentACar.core.utilities.results.SuccessResult;
import com.btkAkademi.rentACar.dataAccess.abstracts.PaymentDao;
import com.btkAkademi.rentACar.entities.concretes.AdditionalService;
import com.btkAkademi.rentACar.entities.concretes.Payment;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class PaymentManager implements PaymentService {
	
	private final PaymentDao paymentDao;
	private final ModelMapperService modelMapperService;
	private final RentalService rentalService;
	private final PosSystemService posSystemService;
	private final CreditCardInfoService creditCardInfoService;
	private final PromotionCodeService promotionCodeService;
	private final InvoiceService invoiceService;

	@Override
	public DataResult<InvoiceListDtoProj> makePayment(CreatePaymentRequest request) {
		
		var promotionCode = request.getCode();
		
		var result= BusinessRules.run(
				checkIfCreditCardValid(request.getCreateCreditCardInfoRequest() ),
				checkIfPromotionCodeValid(promotionCode)
				);
		
		if(result != null) {
			return new ErrorDataResult<InvoiceListDtoProj>(result.getMessage());
		}
		
		//TODO eğer kart sistemde kayıtlı ise, kaydetmeye gerek yok. credit card serviste yazalım bunu.
		if(request.isSaveRequested() ) {
			var card =this.modelMapperService.forDto().map(request.getCreateCreditCardInfoRequest(), CreateCreditCardInfoRequest.class); 
			this.creditCardInfoService.saveCard(card);
		}		
		
		var payment = this.modelMapperService.forRequest().map(request, Payment.class);
		var sum = calculateTotalSum(request.getRentalId());

		if(promotionCode == null || promotionCode == "") {
			
		}else {
			var promotionCodeObj = this.promotionCodeService.getPromotionCodeByCode(promotionCode).getData();
			if(promotionCodeObj != null) {
				byte discountRate = promotionCodeObj.getDiscountRate();
				sum = sum - (sum * discountRate / 100) ;
			}
		}
		
		payment.setTotalSum(sum);
		this.paymentDao.save(payment);
		
		return this.invoiceService.getInvoiceByRentalId(request.getRentalId() );		
	}
	
	//TODO promosyon kod işlemlerini buraya yedirelim.
	private double calculateTotalSum(int rentalId) {
		var rental = this.rentalService.getRentalById(rentalId).getData();
		var car = rental.getCar();
		
		var dayCount = Period.between(rental.getRentDate(), rental.getReturnDate()).getDays();
		if(dayCount == 0) {dayCount=1;}
		
		var carPrice = car.getDailyPrice();
		
		var additionalServices = rental.getAdditionalServices();
		
		double additionalServicesSum = 0;
		for(AdditionalService as : additionalServices) {
			additionalServicesSum+= as.getPrice();
		}		
		return (carPrice + additionalServicesSum)*dayCount;
	}	
	
	private Result checkIfCreditCardValid(CreateCreditCardInfoRequest cardInfo) {
		return this.posSystemService.checkIfCreditCardIsValid(cardInfo);
	}
	
	private Result checkIfPromotionCodeValid(String code) {
		
		if(code == null) {
			return new SuccessResult();
		}
		
		var result = this.promotionCodeService.getPromotionCodeByCode(code);
		if(!result.isSuccess()) {
			return result;
		}
		
		var promotionCode = result.getData();
		
		if(!Period.between(promotionCode.getEndDate(), LocalDate.now()).isNegative() ) {
			return new ErrorResult(Messages.CODEEXPIRED);
		}else if( Period.between(promotionCode.getStartDate(), LocalDate.now()).isNegative() ) {
			return new ErrorResult(Messages.CODETIMENOTSTARTED);
		}
		
		return new SuccessResult();
	}
	
}
