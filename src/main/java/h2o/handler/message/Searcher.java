package h2o.handler.message;

import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@NoArgsConstructor
public class Searcher {

    private final String regexPhone = "[0-9]{10}";
    private final String regexId = "[0-9]*";
    private final String regexEmail = "[^@\\s]+[@][a-zA-Z0-9]+[.][a-z]+";
    private final String regexFullName = "(([a-zA-Zа-яА-Я]+[\\s][a-zA-Zа-яА-Я]+[\\s][a-zA-Zа-яА-Я]+)|([a-zA-Zа-яА-Я]+[\\s][a-zA-Zа-яА-Я]+))";


    public String searchPhone(String message) {
        return searchString(message, regexPhone);
    }

    public String searchFullName(String message){
        return searchString(message, regexFullName);
    }

    public String searchEmail(String message){
        return searchString(message, regexEmail);
    }


    public int searchOrderId(String message) {
        Pattern pattern = Pattern.compile(regexId);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        } else {
            return 0;
        }
    }

    public long searchChatId(String message){
        Pattern pattern = Pattern.compile(regexId);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return Long.parseLong(matcher.group());
        } else {
            return 0;
        }
    }

    private String searchString(String message, String regex){
        Matcher matcher = Pattern.compile(regex).matcher(message);
        if(matcher.find()){
            return matcher.group();
        }
        return null;
    }

}