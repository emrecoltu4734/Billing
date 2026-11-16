package com.ispbilling.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Panel formlarında tutarların Türkçe yazılabilmesi için: "1.234,56", "1234,56" ve "1234.56" kabul edilir.
     * Yalnızca form/parametre bağlamayı etkiler; JSON API'si Jackson kullandığı için etkilenmez.
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatterForFieldType(BigDecimal.class, new Formatter<BigDecimal>() {
            @Override
            public BigDecimal parse(String text, Locale locale) throws ParseException {
                String s = text.replace(" ", "").replace("₺", "").replace("%", "");
                if (s.contains(",")) {
                    s = s.replace(".", "").replace(',', '.');
                }
                try {
                    return new BigDecimal(s);
                } catch (NumberFormatException e) {
                    throw new ParseException(text, 0);
                }
            }

            @Override
            public String print(BigDecimal value, Locale locale) {
                return value.toPlainString();
            }
        });
    }
}
