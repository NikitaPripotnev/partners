package h2o.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Order {
    private long chatId;
    private String typeObject;
    private String typeCleaning;
    private int numberRooms;
    private int area;
    private int ceilingHeight;
    private int numberBathroom;
    private int distance;
    private int paymentAmount;
    private String orderInfo;

    public Order(long chatId){
        this.chatId = chatId;
        this.numberRooms=-1;
    }
}
