package com.bookingsquadra.config;

import com.bookingsquadra.entity.Amenity;
import com.bookingsquadra.entity.Sport;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class EnumConverterConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToSportConverter());
        registry.addConverter(new StringToAmenityConverter());
    }

    static class StringToSportConverter implements Converter<String, Sport> {
        @Override
        public Sport convert(String source) {
            if (source == null || source.isBlank())
                return null;
            return Sport.fromCode(source.trim());
        }
    }

    static class StringToAmenityConverter implements Converter<String, Amenity> {
        @Override
        public Amenity convert(String source) {
            if (source == null || source.isBlank())
                return null;
            return Amenity.fromCode(source.trim());
        }
    }
}
