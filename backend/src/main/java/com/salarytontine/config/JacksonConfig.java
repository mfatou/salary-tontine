package com.salarytontine.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serialisation centralisee de {@link YearMonth} au format "YYYY-MM",
 * unique representation d'un mois acceptee et produite par l'API.
 */
@Configuration
public class JacksonConfig {

    public static final String MONTH_PATTERN_DESCRIPTION = "YYYY-MM";

    @Bean
    public SimpleModule yearMonthModule() {
        SimpleModule module = new SimpleModule("YearMonthModule");
        module.addSerializer(YearMonth.class, new YearMonthSerializer());
        module.addDeserializer(YearMonth.class, new YearMonthDeserializer());
        return module;
    }

    private static final class YearMonthSerializer extends JsonSerializer<YearMonth> {
        @Override
        public void serialize(YearMonth value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(value.format(YearMonthAttributeConverter.FORMATTER));
        }
    }

    private static final class YearMonthDeserializer extends JsonDeserializer<YearMonth> {
        @Override
        public YearMonth deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String raw = parser.getText();
            try {
                return YearMonth.parse(raw.trim(), YearMonthAttributeConverter.FORMATTER);
            } catch (DateTimeParseException | NullPointerException exception) {
                throw new IOException(
                        "Mois invalide : le format attendu est " + MONTH_PATTERN_DESCRIPTION + ".");
            }
        }
    }
}
