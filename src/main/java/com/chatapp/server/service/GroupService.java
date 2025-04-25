package com.chatapp.server.service;

import com.chatapp.common.model.Group;
import com.chatapp.data.dao.GroupDAO;
import com.chatapp.data.dao.impl.GroupDAOImpl;
import com.chatapp.data.db.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class GroupService {
    private final GroupDAO groupDAO;
    private final MessageService messageService;
    
    public GroupService() {
        this.groupDAO = new GroupDAOImpl();
        this.messageService = new MessageService();
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

    /**
     * Delete a group and all its associated messages
     */
    public boolean deleteGroup(String groupName) {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // First delete all messages
                if (!messageService.deleteGroupMessages(groupName)) {
                    throw new SQLException("Failed to delete group messages");
                }

                // Then delete the group
                String sql = "DELETE FROM groups WHERE group_name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, groupName);
                    int rowsAffected = pstmt.executeUpdate();
                    
                    if (rowsAffected > 0) {
                        System.out.println("Successfully deleted group: " + groupName);
                        conn.commit();
                        return true;
                    } else {
                        throw new SQLException("Group not found: " + groupName);
                    }
                }
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("Error deleting group: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Database connection error while deleting group: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}