package h2o.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Partner {
    private long chatId;
    private String fullName;
    private String phoneNumber;
    private String email;
    private int countOrders;
    private int deniedOrders;
    private int successfulOrder;
    private int balance;
    private boolean cashoutStatus;
    private String position;
}
