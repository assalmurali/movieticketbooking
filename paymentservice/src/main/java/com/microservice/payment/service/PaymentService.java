package com.microservice.payment.service;



import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.mtb.booking.util.AppCode.*;
import static com.mtb.booking.util.MessageUtil.SUCCESS;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final HttpServletRequest httpServletRequest;
    private final PaymentRepo paymentRepo;
    private final UserRepo userRepo;
    private final TbookingRepo tbookingRepo;
    private final PaystackFeign paystackFeign;
    private final MessageFeign messageFeign;
    @Value("${API_KEY}")
    private String API_KEY;
    @Value("${API_FROM}")
    private String API_FROM;
    @Value("${API_CHANNELOTP}")
    private String sms;
    @Value("${API_TYPE}")
    private String API_TYPE;
    private String getHeader(){
        return httpServletRequest.getHeader("Authorization");
    }

    private String getPhone(String phone) {
        if(phone.startsWith("+234")){
            return phone.substring(1);
        }
        if(phone.startsWith("234")){
            return phone;
        }

        return "234"+phone.substring(1);

    }
    private void postToPhone(Mrec load) {

        SmsRequest req = SmsRequest.builder()
                .api_key(API_KEY)
                .sms(load.getSms())
                .channel(load.getCategory().equals("sms") ? sms : null)
                .from(API_FROM)
                .to(load.getTo())
                .type(API_TYPE)
                .build();
        LinkedHashMap<String, Object> result = messageFeign.sendMessage(req);
        log.info("{}", result);
    }

    @Override
    public ResponseEntity initiatePayment(String amount, InitiateRequest payload, Long userId, Long ticketId) {
        Optional<Users> usersOptional = userRepo.findById(userId);
        Optional<Tbooking> tbokingOptional = tbookingRepo.findById(ticketId);
        if (usersOptional.isEmpty()) {
            throw new RecordNotFoundException(NOT_FOUND);
        } else {
            Users users = usersOptional.get();
            Tbooking tbooking = tbokingOptional.get();
            amount = payload.getAmount();
            Payment payment = new Payment();
            payment.setAmount(payload.getAmount());
            payment.setCustomer(users);
            payment.setTicket(tbooking);
            payment.setStatus(PaymentStatus.PAID.name());
            payload.setAmount(amount);
            payload.setEmail("atobateleolasunkanmi@yahoo.com");
            payment.setDatecreated(new Date());
            paymentRepo.save(payment);
            InitiateResponse initiate = paystackFeign.initiateTransaction(payload, getHeader());

            if (initiate.getStatus() != null && initiate.getMessage() != null && payment.getStatus().equals("PAID")) {
                postToPhone(Mrec.builder()
                        .category("sms")
                        .sms("You have successfully paid for the movie ticket:" + payment.getTicket().getTbookingMovie().getMovieName())
                        .to(Arrays.asList(getPhone(payment.getCustomer().getUsersMobileNo())))
                        .build());
                return ResponseEntity.ok(new ApiResponse<>("success", "200", initiate));

            }else{

            throw new RecordNotFoundException(NOT_FOUND);

            }

        }
    }



}