package com.centralservicos.pedagogical;
import jakarta.validation.constraints.*;
import java.util.*;

final class AeeViews {
    record StudentInput(@NotBlank @Size(max=160) String name, boolean archived, Long version) {}
    record Student(UUID id, UUID aeeClassId, UUID classId, String name, boolean archived, long version, boolean canManage) {}
    record Classroom(UUID id, String name, boolean archived, boolean canView, boolean canCreateStudents) {}
    record Context(UUID aeeId, String aeeName, UUID classId, String className, Student student,
                   boolean archived, boolean canEdit, boolean canManage, List<PedagogicalViews.Teacher> teachers) {}
}
