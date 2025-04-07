package com.chatapp.common.model;

import java.util.List;

public class Group {
    private String groupName;
    private List<String> membersEmails; 
    // Ou List<User> members si vous préférez stocker des User complets

    public Group(String groupName, List<String> membersEmails) {
        this.groupName = groupName;
        this.membersEmails = membersEmails;
    }

    public String getGroupName() {
        return groupName;
    }
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<String> getMembersEmails() {
        return membersEmails;
    }
    public void setMembersEmails(List<String> membersEmails) {
        this.membersEmails = membersEmails;
    }

    // Vous pouvez rajouter d'autres champs (id, image, etc.)
}
