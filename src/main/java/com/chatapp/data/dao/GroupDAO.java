package com.chatapp.data.dao;

import com.chatapp.common.model.Group;
import java.util.List;

public interface GroupDAO {
    boolean createGroup(Group group);
    Group findGroupByName(String groupName);
    List<Group> getAllGroups();
    List<Group> getGroupsByUser(String userEmail);
    boolean addMemberToGroup(int groupId, String userEmail);
    boolean removeMemberFromGroup(int groupId, String userEmail);
    List<String> getGroupMembers(int groupId);
    boolean deleteGroup(int groupId);
}