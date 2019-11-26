package h2o.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class AdminOrderInfo {
    int orderId;
    boolean approve;
    int amount;
    LocalDateTime dateCleaning;
    String adminComment;
    List<Integer> trashMessages;
}
