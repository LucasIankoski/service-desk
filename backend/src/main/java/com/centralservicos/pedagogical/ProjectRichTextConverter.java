package com.centralservicos.pedagogical;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;

@Converter
public class ProjectRichTextConverter implements AttributeConverter<ProjectViews.RichText, String> {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    public String convertToDatabaseColumn(ProjectViews.RichText value) {
        return JSON.writeValueAsString(value == null ? ProjectViews.RichText.empty() : value);
    }
    public ProjectViews.RichText convertToEntityAttribute(String value) {
        return value == null ? ProjectViews.RichText.empty() : JSON.readValue(value, ProjectViews.RichText.class);
    }
}
