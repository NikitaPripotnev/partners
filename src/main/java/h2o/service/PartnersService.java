package h2o.service;

import h2o.dao.PartnersDao;
import h2o.model.Partner;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class PartnersService {
    private PartnersDao partnersDao;

    public boolean createPartner(Partner newPartner) {
        Partner partner = partnersDao.fetchPartner(newPartner.getChatId());
        if (partner != null) {
            log.warn("Для переданного chatId уже существуте запись");
            return false;
        }
        partnersDao.savePartner(newPartner);
        return true;
    }

    public Partner fecthInfoAboutPartner(long chatId){
        return partnersDao.fetchPartner(chatId);
    }

    public void removePartner(long chatId){
        partnersDao.forgetPartner(chatId);
    }

    public void changeRole(long chatId, String position){
        partnersDao.changeRole(chatId, position);
    }

}
