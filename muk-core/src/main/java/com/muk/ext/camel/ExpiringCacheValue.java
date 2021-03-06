/*******************************************************************************
 * Copyright (C)  2018  mizuuenikaze inc
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
package com.muk.ext.camel;

public class ExpiringCacheValue<V> {

	private long inserted;
	private V value;

	public ExpiringCacheValue() {

	}

	public ExpiringCacheValue(V value) {
		this.value = value;
	}

	public boolean isExpired(long expiration) {
		return System.currentTimeMillis() > (inserted + expiration);
	}

	public long getInserted() {
		return inserted;
	}

	public void setInserted(long inserted) {
		this.inserted = inserted;
	}

	public V getValue() {
		return value;
	}

	public void setValue(V value) {
		this.value = value;
	}
}
