package h2o.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class OperatorOrder {
    private boolean fullOrder;
    private LocalDateTime cleaningDate;
    private String address;
    private String paymentMethod;
    private String typeCleaning;
    private String typeObject;
    private int paymentAmount;
    private String clientName;
    private String clientNumber;
    private String counterparty;
    private String orderSource;
    private String comment;
    private LocalDateTime orderCreationTime;
    private String orderAuthor;
    private int messageId;
    private int groupNumber;
    private int chatIdOwner;
}
