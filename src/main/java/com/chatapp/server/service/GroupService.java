package com.chatapp.server.service;

import com.chatapp.common.model.Group;
import com.chatapp.data.dao.GroupDAO;
import com.chatapp.data.dao.impl.GroupDAOImpl;
import java.util.List;

public class GroupService {
    private final GroupDAO groupDAO;
    
    public GroupService() {
        this.groupDAO = new GroupDAOImpl();
    }
    
    public Group createGroup(String groupName, List<String> members) {
        System.out.println("GroupService: Creating group " + groupName + " with " + members.size() + " members");
        Group group = new Group(groupName, members);
        boolean success = groupDAO.createGroup(group);
        if (success) {
            System.out.println("GroupService: Group created successfully in the database");
            return group;
        }
        System.out.println("GroupService: Failed to create group in database");
        return null;
    }
    
    public Group findGroupByName(String groupName) {
        return groupDAO.findGroupByName(groupName);
    }
    
    public List<Group> getAllGroups() {
        return groupDAO.getAllGroups();
    }
    
    public List<Group> getGroupsByUser(String userEmail) {
        return groupDAO.getGroupsByUser(userEmail);
    }
}