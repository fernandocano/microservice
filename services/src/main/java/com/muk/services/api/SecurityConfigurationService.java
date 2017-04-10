/*******************************************************************************
 * Copyright (C)  2017  mizuuenikaze inc
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.muk.services.api;

public interface SecurityConfigurationService {

	static final String OAUTH_CLIENT_ID = "oauth.clientId";
	static final String OAUTH_SERVER = "oauth.server";
	static final String OAUTH_TOKEN_PATH = "oauth.tokenPath";
	static final String OAUTH_USERINFO_PATH = "oauth.userInfoPath";
	static final String OAUTH_CHECKTOKEN_PATH = "oauth.checkTokenPath";
	static final String OAUTH_SALT = "oauth.salt";

	String getOauthServer();

	String getOauthServiceClientId();

	String getOauthTokenPath();

	String getOauthUserInfoPath();

	String getOauthCheckTokenPath();

	String getSalt();
}
