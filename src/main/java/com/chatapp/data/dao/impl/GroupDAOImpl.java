package com.chatapp.data.dao.impl;

import com.chatapp.common.model.Group;
import com.chatapp.data.dao.GroupDAO;
import com.chatapp.data.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupDAOImpl implements GroupDAO {

    @Override
    public boolean createGroup(Group group) {
        // First create the group
        String insertGroupQuery = "INSERT INTO user_groups (name) VALUES (?)";
        
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);
            
            int groupId;
            try (PreparedStatement stmt = conn.prepareStatement(insertGroupQuery, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, group.getGroupName());
                stmt.executeUpdate();
                
                // Get the generated group ID
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    groupId = rs.getInt(1);
                } else {
                    throw new SQLException("Creating group failed, no ID obtained.");
                }
            }
            
            // Now add all members
            String insertMemberQuery = "INSERT INTO group_members (group_id, user_email) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertMemberQuery)) {
                for (String email : group.getMembersEmails()) {
                    stmt.setInt(1, groupId);
                    stmt.setString(2, email);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            
            conn.commit();
            System.out.println("Group created successfully: " + group.getGroupName() + " with ID: " + groupId);
            return true;
            
        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                System.err.println("Error during rollback: " + ex.getMessage());
            }
            System.err.println("Database error creating group: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    @Override
public boolean deleteGroup(int groupId) {
    Connection conn = null;
    try {
        conn = DatabaseManager.getConnection();
        conn.setAutoCommit(false);
        
        // Delete members first due to foreign key constraint
        String deleteMembersQuery = "DELETE FROM group_members WHERE group_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteMembersQuery)) {
            stmt.setInt(1, groupId);
            stmt.executeUpdate();
        }
        
        // Delete group
        String deleteGroupQuery = "DELETE FROM user_groups WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteGroupQuery)) {
            stmt.setInt(1, groupId);
            int rowsAffected = stmt.executeUpdate();
            
            conn.commit();
            System.out.println("Group with ID " + groupId + " deleted successfully");
            return rowsAffected > 0;
        }
    } catch (SQLException e) {
        try {
            if (conn != null) conn.rollback();
        } catch (SQLException ex) {
            System.err.println("Error during rollback: " + ex.getMessage());
        }
        System.err.println("Database error deleting group: " + e.getMessage());
        e.printStackTrace();
        return false;
    } finally {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                System.err.println("Error resetting connection state: " + e.getMessage());
            }
        }
    }
}

    @Override
    public Group findGroupByName(String groupName) {
        String query = "SELECT id FROM user_groups WHERE name = ?";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, groupName);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                int groupId = rs.getInt("id");
                List<String> members = getGroupMembers(groupId);
                return new Group(groupName, members);
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Database error finding group: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<Group> getAllGroups() {
        String query = "SELECT id, name FROM user_groups";
        List<Group> groups = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                int groupId = rs.getInt("id");
                String groupName = rs.getString("name");
                List<String> members = getGroupMembers(groupId);
                groups.add(new Group(groupName, members));
            }
            
            return groups;
        } catch (SQLException e) {
            System.err.println("Database error retrieving all groups: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public List<Group> getGroupsByUser(String userEmail) {
        String query = "SELECT g.id, g.name FROM user_groups g " +
                      "JOIN group_members gm ON g.id = gm.group_id " +
                      "WHERE gm.user_email = ?";
        List<Group> groups = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, userEmail);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                int groupId = rs.getInt("id");
                String groupName = rs.getString("name");
                List<String> members = getGroupMembers(groupId);
                groups.add(new Group(groupName, members));
            }
            
            return groups;
        } catch (SQLException e) {
            System.err.println("Database error retrieving user's groups: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public boolean addMemberToGroup(int groupId, String userEmail) {
        String query = "INSERT INTO group_members (group_id, user_email) VALUES (?, ?)";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setInt(1, groupId);
            stmt.setString(2, userEmail);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error adding member to group: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean removeMemberFromGroup(int groupId, String userEmail) {
        String query = "DELETE FROM group_members WHERE group_id = ? AND user_email = ?";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setInt(1, groupId);
            stmt.setString(2, userEmail);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error removing member from group: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<String> getGroupMembers(int groupId) {
        String query = "SELECT user_email FROM group_members WHERE group_id = ?";
        List<String> members = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, String.valueOf(groupId));
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                members.add(rs.getString("user_email"));
            }
            
            return members;
        } catch (SQLException e) {
            System.err.println("Database error retrieving group members: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}