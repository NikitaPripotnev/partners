package h2o.calculator;

import h2o.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Calculator {
    private int[] pricesGenereal = {3750, 3750, 5490, 120, 7490, 115, 8790, 110, 100, 90};
    private int[] limitMetrForNumberRooms = {30, 45, 65, 85, 150};

    public int calculateFlat(Order order) {
        int difLimit = defineLimit(order.getNumberRooms(),order.getArea());
        boolean individualCalculate = order.getNumberRooms()==4 || (order.getNumberRooms()!=0&&difLimit==1);
        if(individualCalculate){
            return  (int)(order.getArea()*pricesGenereal[order.getNumberRooms()*2+difLimit]*
                    definePercent(order.getTypeCleaning())+
                    (order.getNumberBathroom()-1)*1000);
        }
        else{
            return  (int)(pricesGenereal[order.getNumberRooms()*2+difLimit]*
                    definePercent(order.getTypeCleaning())+
                    (order.getNumberBathroom()-1)*1000);
        }
    }


    private int defineLimit(int numberRooms, int area) {
        int limit = limitMetrForNumberRooms[numberRooms];
        if (area < limit) {
            return 0;
        } else {
            return 1;
        }
    }

    private double definePercent(String typeCleaning){
        if(typeCleaning.equals("Генеральная (с окнами)")){
            return 1.3;
        }
        if(typeCleaning.equals("Генеральная (люкс)")){
            return 1.7;
        }
        return 1;
    }
}
