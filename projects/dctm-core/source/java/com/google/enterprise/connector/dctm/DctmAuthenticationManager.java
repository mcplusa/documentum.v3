// Copyright (C) 2006-2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.dctm;

import com.google.common.base.Strings;
import com.google.enterprise.connector.dctm.dfcwrap.IClientX;
import com.google.enterprise.connector.dctm.dfcwrap.ICollection;
import com.google.enterprise.connector.dctm.dfcwrap.IGroup;
import com.google.enterprise.connector.dctm.dfcwrap.ILoginInfo;
import com.google.enterprise.connector.dctm.dfcwrap.IPersistentObject;
import com.google.enterprise.connector.dctm.dfcwrap.IQuery;
import com.google.enterprise.connector.dctm.dfcwrap.ISession;
import com.google.enterprise.connector.dctm.dfcwrap.ISessionManager;
import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthenticationResponse;
import com.google.enterprise.connector.spi.Principal;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryLoginException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants.CaseSensitivityType;
import com.google.enterprise.connector.spi.SpiConstants.PrincipalType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

public class DctmAuthenticationManager implements AuthenticationManager {
  private static final Logger LOGGER = 
      Logger.getLogger(DctmAuthenticationManager.class.getName());

  private final IClientX clientX;

  private final String docbase;

  private final DctmConnector connector;

  public DctmAuthenticationManager(DctmConnector connector, IClientX clientX,
      String docbase) {
    this.clientX = clientX;
    this.docbase = docbase;
    this.connector = connector;
  }

  public AuthenticationResponse authenticate(
      AuthenticationIdentity authenticationIdentity)
      throws RepositoryLoginException, RepositoryException {
    String username = authenticationIdentity.getUsername();
    LOGGER.info("authentication process for user " + username);
    String password = authenticationIdentity.getPassword();
    ISessionManager sessionManagerUser;
    boolean authenticate;

    try {
      if (Strings.isNullOrEmpty(password)) {
        sessionManagerUser =
            getSessionManager(connector.getLogin(), connector.getPassword());
        //check for user existence when null password
        authenticate = isValidUser(sessionManagerUser.getSession(docbase),
            username);
      } else {
        sessionManagerUser = getSessionManager(username, password);
        authenticate = sessionManagerUser.authenticate(docbase);
      }
    } catch (RepositoryLoginException e) {
      LOGGER.finer(e.getMessage());
      LOGGER.info("authentication status: false");
      return new AuthenticationResponse(false, "");
    }

    LOGGER.info("authentication status: " + authenticate);
    if (authenticate) {
      return new AuthenticationResponse(authenticate, "", getAllGroupsForUser(
          sessionManagerUser, username));
    } else {
      return new AuthenticationResponse(false, "");
    }
  }

  private ISessionManager getSessionManager(String username, String password)
      throws RepositoryLoginException, RepositoryException {
    ILoginInfo loginInfo = clientX.getLoginInfo();
    loginInfo.setUser(username);
    loginInfo.setPassword(password);
    ISessionManager sessionManagerUser =
        clientX.getLocalClient().newSessionManager();
    sessionManagerUser.setIdentity(docbase, loginInfo);
    return sessionManagerUser;
  }

  private boolean isValidUser(ISession session, String userName)
      throws RepositoryDocumentException {
    IPersistentObject userObj = session.getObjectByQualification(
          "dm_user where user_name = '" + userName + "'");
    return userObj != null;
  }

  /**
   * Gets all groups the user belongs to.
   *
   * @param a session manager for the given user
   * @param username a user name
   * @return Collection of Principals
   */
  private Collection<Principal> getAllGroupsForUser(
      ISessionManager sessionManager, String username)
      throws RepositoryLoginException, RepositoryException {
    ArrayList<Principal> listGroups = new ArrayList<Principal>();
    String queryStr =
        "select group_name from dm_group where any i_all_users_names='"
            + username + "'";
    IQuery query = clientX.getQuery();
    query.setDQL(queryStr);
    ICollection collecGroups =
        query.execute(sessionManager.getSession(docbase),
            IQuery.EXECUTE_READ_QUERY);
    if (collecGroups != null) {
      try {
        while (collecGroups.next()) {
          String groupName = collecGroups.getString("group_name");
          String groupNamespace =
              getGroupNamespace(sessionManager.getSession(docbase), groupName);
          if (groupNamespace != null) {
            listGroups.add(new Principal(PrincipalType.UNKNOWN, groupNamespace,
                groupName, CaseSensitivityType.EVERYTHING_CASE_SENSITIVE));
          } else {
            LOGGER.warning("Skipping group " + groupName
                + " with null namespace");
          }
        }
        // process special group dm_world
        listGroups.add(new Principal(PrincipalType.UNKNOWN, connector
            .getGoogleLocalNamespace(), "dm_world",
            CaseSensitivityType.EVERYTHING_CASE_SENSITIVE));
      } finally {
        collecGroups.close();
      }
    }
    return listGroups;
  }

  //TODO (Srinivas): refactor this method with the one in DctmAclList
  private String getGroupNamespace(ISession session, String groupName)
      throws RepositoryDocumentException {
    String localNamespace = connector.getGoogleLocalNamespace();
    String globalNamespace = connector.getGoogleGlobalNamespace();
    String groupNamespace = null;
    try {
      IGroup groupObj = (IGroup) session.getObjectByQualification(
          "dm_group where group_name = '" + groupName + "'");
      if (groupObj != null) {
        if (Strings.isNullOrEmpty(groupObj.getUserSource())) {
          LOGGER.fine("local namespace for group " + groupName);
          groupNamespace = localNamespace;
        } else {
          LOGGER.fine("global namespace for group " + groupName);
          groupNamespace = globalNamespace;
        }
      }
    } catch (RepositoryDocumentException e) {
      LOGGER.fine("Exception in getNameSpace " + e.getMessage());
      throw e;
    }
    return groupNamespace;
  }
}
