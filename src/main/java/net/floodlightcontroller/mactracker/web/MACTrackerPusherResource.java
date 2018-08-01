package net.floodlightcontroller.mactracker.web;

import java.io.IOException;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.mactracker.StaticEntries;
import net.floodlightcontroller.mactracker.MACTrackerPusher;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.util.MatchUtils;

/**
 * Pushes a static flow entry to the storage source
 */
public class MACTrackerPusherResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(MACTrackerPusherResource.class);

	/**
	 * Takes a Static Entry Pusher string in JSON format and parses it into
	 * our database schema then pushes it to the database.
	 * @param fmJson The Static Flow Pusher entry in JSON format.
	 * @return A string status message
	 */
	@Post
	public String store(String json) {
		IStorageSourceService storageSource =
				(IStorageSourceService)getContext().getAttributes().
				get(IStorageSourceService.class.getCanonicalName());

		Map<String, Object> rowValues;
		try {
			rowValues = StaticEntries.jsonToStorageEntry(json);
			String status = null;

			int state = checkFlow(rowValues);
			if (state == 1) {
				status = "Warning! Must specify eth_type of IPv4/IPv6 to " +
						"match on IPv4/IPv6 fields! The flow has been discarded.";
				log.error(status);
			} else if (state == 2) {
				status = "Warning! eth_type not recognized! The flow has been discarded.";
				log.error(status);
			} else if (state == 3) {
				status = "Warning! Must specify ip_proto to match! The flow has been discarded.";
				log.error(status);
			} else if (state == 4) {
				status = "Warning! ip_proto invalid! The flow has been discarded.";
				log.error(status);
			} else if (state == 5) {
				status = "Warning! Must specify icmp6_type to match! The flow has been discarded.";
				log.error(status);
			} else if (state == 6) {
				status = "Warning! icmp6_type invalid! The flow has been discarded.";
				log.error(status);
			} else if (state == 7) {
				status = "Warning! IPv4 & IPv6 fields cannot be specified in the same flow! The flow has been discarded.";
				log.error(status);
			} else if (state == 8) {
				status = "Warning! Must specify switch DPID in flow. The flow has been discarded.";
				log.error(status);
			} else if (state == 9) {
				status = "Warning! Switch DPID invalid! The flow has been discarded.";
				log.error(status);
			} else if (state == 0) {
				status = "Entry pushed";            
				storageSource.insertRowAsync(MACTrackerPusher.TABLE_NAME, rowValues);
			}
			return ("{\"status\" : \"" + status + "\"}");
		} catch (IOException e) {
			log.error("Error parsing push flow mod request: " + json, e);
			return "{\"status\" : \"Error! Could not parse flow mod, see log for details.\"}";
		}        
	}

	@Delete
	public String del(String json) {
		IStorageSourceService storageSource =
				(IStorageSourceService)getContext().getAttributes().
				get(IStorageSourceService.class.getCanonicalName());
		String fmName = null;
		if (json == null) {
			return "{\"status\" : \"Error! No data posted.\"}";
		}
		try {
			fmName = StaticEntries.getEntryNameFromJson(json);
			if (fmName == null) {
				return "{\"status\" : \"Error deleting entry, no name provided\"}";
			}
		} catch (IOException e) {
			log.error("Error deleting flow mod request: " + json, e);
			return "{\"status\" : \"Error deleting entry, see log for details\"}";
		}

		storageSource.deleteRowAsync(MACTrackerPusher.TABLE_NAME, fmName);
		return "{\"status\" : \"Entry " + fmName + " deleted\"}";
	}
}