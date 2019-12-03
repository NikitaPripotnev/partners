package h2o.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
public class TmpInfo {
    private int orderId;
    private int messageId;
    private String cooment;
    private boolean completeCheck;
    private boolean deniedCheck;
    private List<Integer> trashMessages;

    public TmpInfo(){
        trashMessages = new ArrayList<>();
    }

    public void addTrashMessage(int messageId){
        trashMessages.add(messageId);
    }
}
