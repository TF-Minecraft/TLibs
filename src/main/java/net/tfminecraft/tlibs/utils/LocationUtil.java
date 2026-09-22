package net.tfminecraft.tlibs.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.util.Vector;

import net.tfminecraft.tlibs.enums.NSEW;

public class LocationUtil {
	public static List<NSEW> getProbableDirection(Vector direction, boolean halves) {
	    double yaw = getYaw(direction); // Get yaw angle from the vector

	    // Determine the closest main direction
	    NSEW primary;
	    if (yaw >= -45 && yaw < 45) {
	        primary = NSEW.SOUTH;
	    } else if (yaw >= 45 && yaw < 135) {
	        primary = NSEW.WEST;
	    } else if (yaw >= 135 || yaw < -135) {
	        primary = NSEW.NORTH;
	    } else {
	        primary = NSEW.EAST;
	    }

	    // Define order (most probable first, opposite last)
	    List<NSEW> orderedDirections = new ArrayList<>();
	    orderedDirections.add(primary);
	    
	    if(halves) {
	    	switch (primary) {
			    case NORTH:
			        orderedDirections.addAll(Arrays.asList(
			            NSEW.NORTH_WEST, NSEW.NORTH_EAST, NSEW.WEST, NSEW.EAST, 
			            NSEW.SOUTH_WEST, NSEW.SOUTH_EAST
			        ));
			        break;
		
			    case SOUTH:
			        orderedDirections.addAll(Arrays.asList(
			            NSEW.SOUTH_WEST, NSEW.SOUTH_EAST, NSEW.WEST, NSEW.EAST, 
			            NSEW.NORTH_WEST, NSEW.NORTH_EAST
			        ));
			        break;
		
			    case EAST:
			        orderedDirections.addAll(Arrays.asList(
			            NSEW.NORTH_EAST, NSEW.SOUTH_EAST, NSEW.NORTH, NSEW.SOUTH, 
			            NSEW.NORTH_WEST, NSEW.SOUTH_WEST
			        ));
			        break;
		
			    case WEST:
			        orderedDirections.addAll(Arrays.asList(
			            NSEW.NORTH_WEST, NSEW.SOUTH_WEST, NSEW.NORTH, NSEW.SOUTH, 
			            NSEW.NORTH_EAST, NSEW.SOUTH_EAST
			        ));
			        break;
				default:
					break;
			}
	    } else {
	    	switch (primary) {
			    case NORTH:
			        orderedDirections.addAll(Arrays.asList(
			            NSEW.WEST, NSEW.EAST
			        ));
			        break;
		
			    case SOUTH:
			        orderedDirections.addAll(Arrays.asList(
			            NSEW.WEST, NSEW.EAST
			        ));
			        break;
		
			    case EAST:
			        orderedDirections.addAll(Arrays.asList(
			            NSEW.NORTH, NSEW.SOUTH
			        ));
			        break;
		
			    case WEST:
			        orderedDirections.addAll(Arrays.asList(
			            NSEW.NORTH, NSEW.SOUTH
			        ));
			        break;
				default:
					break;
			}
	    }
	    
	    


	    return orderedDirections;
	}

	
	private static double getYaw(Vector direction) {
	    return Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
	}

}
