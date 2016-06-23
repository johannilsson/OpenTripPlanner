package org.opentripplanner.updater;

import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import org.onebusaway.gtfs.model.*;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.serialization.mappings.StopTimeFieldMappingFactory;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.opentripplanner.routing.graph.GraphIndex;
import org.opentripplanner.routing.trippattern.TripTimes;

import java.text.ParseException;
import java.util.BitSet;
import java.util.List;

/**
 * This class is used for matching TripDescriptors without trip_ids to scheduled GTFS data and to
 * feed back that information into a new TripDescriptor with proper trip_id.
 *
 * The class should only be used if we know that the feed producer is unable to produce trip_ids
 * in the GTFS-RT feed.
 */
public class GtfsRealtimeFuzzyTripMatcher {

    private GraphIndex index;

    public GtfsRealtimeFuzzyTripMatcher(GraphIndex index) {
        this.index = index;
    }

    public TripDescriptor match(String feedId, TripDescriptor trip) {
        if (trip.hasTripId()) {
            // trip_id already exists
            return trip;
        }

        if (!trip.hasRouteId() || !trip.hasDirectionId() ||
                !trip.hasStartTime() || !trip.hasStartDate()) {
            // Could not determine trip_id, returning original TripDescriptor
            return trip;
        }

        AgencyAndId routeId = new AgencyAndId(feedId, trip.getRouteId());
        int time = StopTimeFieldMappingFactory.getStringAsSeconds(trip.getStartTime());
        ServiceDate date;
        try {
            date = ServiceDate.parseString(trip.getStartDate());
        } catch (ParseException e) {
            return trip;
        }
        Route route = index.routeForId.get(routeId);
        if (route == null) {
            return trip;
        }
        int direction = trip.getDirectionId();

        Trip matchedTrip = getTrip(route, direction, time, date);

        if (matchedTrip == null) {
            // Check if the trip is carried over from previous day
            date = date.previous();
            time += 24*60*60;
            matchedTrip = getTrip(route, direction, time, date);
        }

        if (matchedTrip == null) {
            return trip;
        }

        // If everything succeeds, build a new TripDescriptor with the matched trip_id
        return trip.toBuilder().setTripId(matchedTrip.getId().getId()).build();

    }

    public Trip getTrip (Route route, int direction,
                          int startTime, ServiceDate date) {
        BitSet services = index.servicesRunning(date);
        for (TripPattern pattern : index.patternsForRoute.get(route)) {
            if (pattern.directionId != direction) continue;
            for (TripTimes times : pattern.scheduledTimetable.tripTimes) {
                if (times.getScheduledDepartureTime(0) == startTime &&
                        services.get(times.serviceCode)) {
                    return times.trip;
                }
            }
        }
        return null;
    }

    boolean checkIfTimeMatch(TripTimes times, int stopIndex, int time, boolean isDeparture) {
        if (isDeparture) {
            return times.getScheduledDepartureTime(stopIndex) == time;
        }
        return times.getScheduledArrivalTime(stopIndex) == time;
    }

    static boolean isBlank(String s) {
        return s == null || "".equals(s);
    }

    public Trip getTrip(Agency agency, Stop stop, List<Integer> routeTypes,
                        String tripShortName, String routeShortName,
                        int direction, int time, ServiceDate date, boolean isDeparture) {
        BitSet services = index.servicesRunning(date);
        for (Route route : index.routesForStop(stop)) {
            if (agency != null && !agency.equals(route.getAgency())) {
                continue;
            }

            if (!isBlank(routeShortName) && !isBlank(route.getShortName())) {
                if (!route.getShortName().equals(routeShortName)) {
                    continue;
                }
            }
            if (!routeTypes.contains(route.getType())) {
                continue;
            }
            for (TripPattern pattern : index.patternsForRoute.get(route)) {
                if (pattern.directionId != direction) {
                    continue;
                }
                for (TripTimes times : pattern.scheduledTimetable.tripTimes) {
                    for (int stopIndex = 0; stopIndex < times.getNumStops(); stopIndex++) {
                        if (checkIfTimeMatch(times, stopIndex, time, isDeparture) &&
                                services.get(times.serviceCode)) {
                            Stop compareStop = pattern.getStop(stopIndex);
                            if (compareStop.getId().equals(stop.getId())) {
                                Trip trip = times.trip;
                                if (isBlank(tripShortName) || isBlank(trip.getTripShortName())) {
                                    return trip;
                                }
                                if (trip.getTripShortName().equals(tripShortName)) {
                                    return trip;
                                }
                            }
                        }
                    }
            }
            }
        }
        return null;
    }

}
