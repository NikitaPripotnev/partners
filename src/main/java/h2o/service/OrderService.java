package h2o.service;

import h2o.dao.OrderDao;
import h2o.model.Order;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class OrderService {
    private OrderDao orderDao;

    public int createOrder(Order order){
        return orderDao.saveOrder(order);
    }

    public boolean changeStatusCompleted(int orderId){
        return orderDao.changeStatus(orderId, "COMPLETED");
    }
    public boolean changeStatusApprove(int orderId){
        return orderDao.changeStatus(orderId, "APPROVE");
    }
    public boolean changeStatusDenied(int orderId){
        return orderDao.changeStatus(orderId, "DENIED");
    }
}
