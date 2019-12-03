package h2o.service;

import h2o.dao.OperatorOrderDao;
import h2o.model.OperatorOrder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class OperatorOrderService {
    private OperatorOrderDao operatorOrderDao;

    public int createOrder(OperatorOrder order) {
        return operatorOrderDao.saveOrder(order);
    }
}
