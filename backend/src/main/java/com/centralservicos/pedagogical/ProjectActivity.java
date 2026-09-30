package com.centralservicos.pedagogical;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;

@Embeddable
class ProjectActivity {
    LocalDate activityDate;
    String title;
    @Convert(converter=ProjectRichTextConverter.class)
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) ProjectViews.RichText description;
    protected ProjectActivity() {}
    ProjectActivity(ProjectViews.Activity input) { activityDate = input.date(); title = input.title(); description = input.description(); }
    ProjectViews.Activity view() { return new ProjectViews.Activity(activityDate, java.util.Objects.toString(title, ""), description); }
}
