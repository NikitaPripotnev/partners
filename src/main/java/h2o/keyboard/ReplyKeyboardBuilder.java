package h2o.keyboard;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

public class ReplyKeyboardBuilder {
    private List<KeyboardRow> keyboard = new ArrayList<>();
    private KeyboardRow row = null;

    private ReplyKeyboardBuilder() {
    }

    public static ReplyKeyboardBuilder create() {
        ReplyKeyboardBuilder builder = new ReplyKeyboardBuilder();
        return builder;
    }

    public ReplyKeyboardBuilder row() {
        this.row = new KeyboardRow();
        return this;
    }

    public ReplyKeyboardBuilder button(String text) {
        row.add(new KeyboardButton(text));
        return this;
    }

    public ReplyKeyboardBuilder endRow() {
        this.keyboard.add(this.row);
        this.row = null;
        return this;
    }

    public ReplyKeyboardMarkup build() {

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();

        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);

        return keyboardMarkup;
    }

    public static ReplyKeyboardMarkup collectKeyboard(List<String> buttonsText){

        List<KeyboardRow> keyboard = new ArrayList<>();
        for(String buttonText : buttonsText){
            KeyboardRow keyboardRow = new KeyboardRow();
            keyboardRow.add(buttonText);
            keyboard.add(keyboardRow);
        }
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        return keyboardMarkup;
    }


}
