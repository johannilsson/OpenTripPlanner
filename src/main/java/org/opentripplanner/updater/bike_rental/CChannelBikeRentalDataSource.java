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

package org.opentripplanner.updater.bike_rental;

import org.opentripplanner.routing.bike_rental.BikeRentalStation;
import org.opentripplanner.util.NonLocalizedString;

import java.util.HashSet;
import java.util.Map;

public class CChannelBikeRentalDataSource extends GenericXmlBikeRentalDataSource {
    public CChannelBikeRentalDataSource() {
        super("//racks/station");
    }

    /*
    <station>
        <rack_id>1</rack_id>
        <description>[1] Allmänna gränd Gröna Lund</description>
        <longitude>18.0948995</longitude>
        <latitude>59.3242468</latitude>
        <color>green</color>
        <last_update>2015-06-08 12:50:01</last_update>
        <online>1</online>
    </station>
     */

    public BikeRentalStation makeStation(Map<String, String> attributes) {
        if (!attributes.get("online").equals("1")) {
            return null;
        }

        BikeRentalStation brstation = new BikeRentalStation();
        brstation.id = attributes.get("rack_id");
        brstation.name = new NonLocalizedString(attributes.get("description"));
        brstation.x = Double.parseDouble(attributes.get("longitude"));
        brstation.y = Double.parseDouble(attributes.get("latitude"));

        brstation.networks = new HashSet<String>();
        brstation.networks.add("CCHANNEL");

        brstation.realTimeData = false;
        brstation.bikesAvailable = 1;
        brstation.spacesAvailable = 1;

        return brstation;
    }
}
