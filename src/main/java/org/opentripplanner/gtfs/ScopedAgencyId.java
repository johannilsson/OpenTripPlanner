/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.gtfs;

import com.google.common.base.Preconditions;

/**
 * Helper to construct a feed scoped agency id.
 */
public class ScopedAgencyId {
    public static final String ID_SEPARATOR = ":";

    /**
     * Create a scoped agency id.
     *
     * @param feedId A feed id
     * @param agencyId A agency id
     * @return A scoped agency id
     */
    public static String create(String feedId, String agencyId) {
        Preconditions.checkNotNull(feedId, "feedId must not be null");
        Preconditions.checkNotNull(agencyId, "agencyId must not be null");
        return String.join(ID_SEPARATOR, feedId, agencyId);
    }
}
