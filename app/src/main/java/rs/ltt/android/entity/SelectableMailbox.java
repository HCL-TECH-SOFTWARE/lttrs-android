package rs.ltt.android.entity;

import com.google.common.base.Objects;

import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRoleAndName;
import rs.ltt.jmap.common.entity.Role;

public class SelectableMailbox implements IdentifiableMailboxWithRoleAndName {

    private final String id;
    private final String name;
    private final Role role;
    private final boolean selected;

    public SelectableMailbox(String id, String name, Role role, boolean selected) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.selected = selected;
    }

    public static SelectableMailbox of(IdentifiableMailboxWithRoleAndName mailbox, boolean selected) {
        return new SelectableMailbox(
                mailbox.getId(),
                mailbox.getName(),
                mailbox.getRole(),
                selected
        );
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Role getRole() {
        return role;
    }

    @Override
    public String getId() {
        return id;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SelectableMailbox that = (SelectableMailbox) o;
        return selected == that.selected &&
                Objects.equal(id, that.id) &&
                Objects.equal(name, that.name) &&
                role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, name, role, selected);
    }
}
