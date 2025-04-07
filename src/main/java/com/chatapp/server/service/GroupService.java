package com.chatapp.server.service;

import com.chatapp.common.model.Group;
import java.util.ArrayList;
import java.util.List;

public class GroupService {
    private List<Group> groups = new ArrayList<>();

    // Vous pouvez charger/voir si vous stockez en JSON ou base de données
    // Ici on va faire simple et garder en mémoire

    public Group createGroup(String groupName, List<String> members) {
        Group group = new Group(groupName, members);
        groups.add(group);
        return group;
    }

    public List<Group> getAllGroups() {
        return groups;
    }

    public Group findGroupByName(String groupName) {
        for (Group g : groups) {
            if (g.getGroupName().equalsIgnoreCase(groupName)) {
                return g;
            }
        }
        return null;
    }
}
