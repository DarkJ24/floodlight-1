package net.floodlightcontroller.MACTracker;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.mactracker.web.MACTrackerPusherResource;
import net.floodlightcontroller.util.ActionUtils;
import net.floodlightcontroller.util.GroupUtils;
import net.floodlightcontroller.util.InstructionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFGroupMod;
import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionClearActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionExperimenter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionStatTrigger;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteMetadata;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

/**
 * Represents static entries to be maintained by the controller on the 
 * switches. 
 */
public class StaticEntries {
	protected static Logger log = LoggerFactory.getLogger(StaticEntries.class);
	private static final int INFINITE_TIMEOUT = 0;

	/**
	 * This function generates a random hash for the bottom half of the cookie
	 * 
	 * @param fm
	 * @param userCookie
	 * @param name
	 * @return A cookie that encodes the application ID and a hash
	 */
	public static U64 computeEntryCookie(int userCookie, String name) {
		// flow-specific hash is next 20 bits
		int prime = 211;
		int hash = 2311;
		for (int i = 0; i < name.length(); i++) {
			hash = hash * prime + (int) name.charAt(i);
		}

		return AppCookie.makeCookie(MACTrackerPusher.STATIC_ENTRY_APP_ID, hash);
	}

	/**
	 * Sets defaults for an OFFlowMod used in the StaticFlowEntryPusher
	 * @param fm The OFFlowMod to set defaults for
	 * @param entryName The name of the entry. Used to compute the cookie.
	 */
	public static void initDefaultFlowMod(OFFlowMod.Builder fmb, String entryName) {
		fmb.setIdleTimeout(INFINITE_TIMEOUT) // not setting these would also work
		.setHardTimeout(INFINITE_TIMEOUT)
		.setBufferId(OFBufferId.NO_BUFFER)
		.setOutPort(OFPort.ANY) 
		.setCookie(computeEntryCookie(0, entryName))
		.setPriority(Integer.MAX_VALUE)
		.setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM));
		return;
	}

	/**
	 * Gets the entry name of a message
	 * @param fmJson The OFFlowMod in a JSON representation
	 * @return The name of the OFFlowMod, null if not found
	 * @throws IOException If there was an error parsing the JSON
	 */
	public static String getEntryNameFromJson(String fmJson) throws IOException{
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;

		try {
			jp = f.createParser(fmJson);
		} catch (JsonParseException e) {
			throw new IOException(e);
		}

		jp.nextToken();
		if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
			throw new IOException("Expected START_OBJECT");
		}

		while (jp.nextToken() != JsonToken.END_OBJECT) {
			if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
				throw new IOException("Expected FIELD_NAME");
			}

			String n = jp.getCurrentName();
			jp.nextToken();
			if (jp.getText().equals("")) 
				continue;

			if (n == MACTrackerPusher.Columns.COLUMN_NAME)
				return jp.getText();
		}
		return null;
	}
	
	public static Map<String, Object> groupModToStorageEntry(OFGroupMod gm, String sw, String name) {
		Map<String, Object> entry = new HashMap<String, Object>();
		entry.put(MACTrackerPusher.Columns.COLUMN_NAME, name);
		entry.put(MACTrackerPusher.Columns.COLUMN_SWITCH, sw);
		entry.put(MACTrackerPusher.Columns.COLUMN_ACTIVE, Boolean.toString(true));
		
		entry.put(MACTrackerPusher.Columns.COLUMN_GROUP_ID, Integer.toString(gm.getGroup().getGroupNumber()));
		entry.put(MACTrackerPusher.Columns.COLUMN_GROUP_TYPE, GroupUtils.groupTypeToString(gm.getGroupType()));
		entry.put(MACTrackerPusher.Columns.COLUMN_GROUP_BUCKETS, GroupUtils.groupBucketsToJsonArray(gm.getBuckets()));
		
		return entry;
	}

	/**
	 * Parses an OFFlowMod (and it's inner Match) to the storage entry format.
	 * @param fm The FlowMod to parse
	 * @param sw The switch the FlowMod is going to be installed on
	 * @param name The name of this static flow entry
	 * @return A Map representation of the storage entry 
	 */
	public static Map<String, Object> flowModToStorageEntry(OFFlowMod fm, String sw, String name) throws Exception {
		Map<String, Object> entry = new HashMap<String, Object>();
		entry.put(MACTrackerPusher.Columns.COLUMN_NAME, name);
		entry.put(MACTrackerPusher.Columns.COLUMN_SWITCH, sw);
		entry.put(MACTrackerPusher.Columns.COLUMN_ACTIVE, Boolean.toString(true));
		entry.put(MACTrackerPusher.Columns.COLUMN_PRIORITY, Integer.toString(fm.getPriority()));
		entry.put(MACTrackerPusher.Columns.COLUMN_IDLE_TIMEOUT, Integer.toString(fm.getIdleTimeout()));
		entry.put(MACTrackerPusher.Columns.COLUMN_HARD_TIMEOUT, Integer.toString(fm.getHardTimeout()));
		entry.put(MACTrackerPusher.Columns.COLUMN_COOKIE, Long.toString(fm.getCookie().getValue()));

		switch (fm.getVersion()) {
		case OF_10:
			if (fm.getActions() != null) {
				entry.put(MACTrackerPusher.Columns.COLUMN_ACTIONS, ActionUtils.actionsToString(fm.getActions()));
			}
			break;
		case OF_11:
		case OF_12:
		case OF_13:
		case OF_14:
		case OF_15:
			/* should have a table ID present */
			if (fm.getTableId() != null) { /* if not set, then don't worry about it. Default will be set when built and sent to switch */
				entry.put(MACTrackerPusher.Columns.COLUMN_TABLE_ID, Short.toString(fm.getTableId().getValue()));
			}
			/* should have a list of instructions, of which apply and write actions could have sublists of actions */
			if (fm.getInstructions() != null) {
				List<OFInstruction> instructions = fm.getInstructions();
				for (OFInstruction inst : instructions) {
					switch (inst.getType()) {
					case GOTO_TABLE:
						entry.put(MACTrackerPusher.intructionToColumnName(OFInstructionType.GOTO_TABLE), 
								InstructionUtils.gotoTableToString(((OFInstructionGotoTable) inst)));
						break;
					case WRITE_METADATA:
						entry.put(MACTrackerPusher.intructionToColumnName(OFInstructionType.WRITE_METADATA), 
								InstructionUtils.writeMetadataToString(((OFInstructionWriteMetadata) inst)));
						break;
					case WRITE_ACTIONS:
						entry.put(MACTrackerPusher.intructionToColumnName(OFInstructionType.WRITE_ACTIONS), 
								InstructionUtils.writeActionsToString(((OFInstructionWriteActions) inst)));
						break;
					case APPLY_ACTIONS:
						entry.put(MACTrackerPusher.intructionToColumnName(OFInstructionType.APPLY_ACTIONS), 
								InstructionUtils.applyActionsToString(((OFInstructionApplyActions) inst)));
						break;
					case CLEAR_ACTIONS:
						entry.put(MACTrackerPusher.intructionToColumnName(OFInstructionType.CLEAR_ACTIONS), 
								InstructionUtils.clearActionsToString(((OFInstructionClearActions) inst)));
						break;
					case METER:
						entry.put(MACTrackerPusher.intructionToColumnName(OFInstructionType.METER), 
								InstructionUtils.meterToString(((OFInstructionMeter) inst)));
						break;
					case EXPERIMENTER:
						entry.put(MACTrackerPusher.intructionToColumnName(OFInstructionType.EXPERIMENTER), 
								InstructionUtils.experimenterToString(((OFInstructionExperimenter) inst)));
						break;
					case DEPRECATED:
						entry.put(MACTrackerPusher.intructionToColumnName(OFInstructionType.DEPRECATED), 
								InstructionUtils.deprecatedToString(((OFInstruction) inst)));
						break;
					case STAT_TRIGGER:
						entry.put(MACTrackerPusher.intructionToColumnName(OFInstructionType.STAT_TRIGGER), 
								InstructionUtils.statTriggerToJsonString(((OFInstructionStatTrigger) inst)));
						break;
					}
				}
			}	
		}		

		Match match = fm.getMatch();
		Iterator<MatchField<?>> itr = match.getMatchFields().iterator(); // only get exact or masked fields (not fully wildcarded)
		while (itr.hasNext()) {
			MatchField<?> mf = itr.next();
			
			String column = MACTrackerPusher.matchFieldToColumnName(mf.id);
			if (match.supports(mf) && match.isExact(mf)) {
				entry.put(column, match.get(mf).toString());
			} else if (match.supportsMasked(mf) && match.isPartiallyMasked(mf)) {
				entry.put(column, match.getMasked(mf).toString());
			} else {
				log.error("Got match for {} but protocol {} does not support said match. Ignoring match.", 
						column, match.getVersion().toString());
			}
		}
				
		int result = MACTrackerPusherResource.checkActions(entry);
		if (result == -1)
			throw new Exception("Invalid action/instructions");
		
		return entry;
	}

	/**
	 * Turns a JSON formatted Static Flow Pusher string into a storage entry
	 * Expects a string in JSON along the lines of:
	 *        {
	 *            "switch":       "AA:BB:CC:DD:EE:FF:00:11",
	 *            "name":         "flow-mod-1",
	 *            "cookie":       "0",
	 *            "priority":     "32768",
	 *            "in_port":	  "1",
	 *            "actions":      "output=2",
	 *        }
	 * @param fmJson The JSON formatted static flow pusher entry
	 * @return The map of the storage entry
	 * @throws IOException If there was an error parsing the JSON
	 */
	public static Map<String, Object> jsonToStorageEntry(String fmJson) throws IOException {
		Map<String, Object> entry = new HashMap<String, Object>();
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;
		
		String tpSrcPort = null;
		String tpDstPort = null;
		String ipProto = null;

		try {
			jp = f.createParser(fmJson);
		} catch (JsonParseException e) {
			throw new IOException(e);
		}

		jp.nextToken();
		if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
			throw new IOException("Expected START_OBJECT");
		}

		while (jp.nextToken() != JsonToken.END_OBJECT) {
			if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
				throw new IOException("Expected FIELD_NAME");
			}

			String n = jp.getCurrentName().toLowerCase().trim();
			jp.nextToken();

			if (n.equals(MACTrackerPusher.Columns.COLUMN_GROUP_BUCKETS)) {
				entry.put(n, jp.readValueAsTree().toString()); /* Special case to save the entire JSON bucket tree */
			} else if (n.equals(MACTrackerPusher.Columns.COLUMN_TP_SRC)) {
				entry.put(n, jp.getText()); /* Support for OF1.0 generic transport ports */
				tpSrcPort = jp.getText();
			} else if (n.equals(MACTrackerPusher.Columns.COLUMN_TP_DST)) {
				entry.put(n, jp.getText()); /* Support for OF1.0 generic transport ports */
				tpDstPort = jp.getText();
			} else if (n.equals(MACTrackerPusher.matchFieldToColumnName(MatchFields.IP_PROTO))) {
				entry.put(MACTrackerPusher.matchFieldToColumnName(MatchFields.IP_PROTO), jp.getText());
				ipProto = jp.getText(); /* Support for OF1.0 generic transport ports */
			} else { 
				entry.put(n, jp.getText()); /* All others are 'key':'value' pairs */
			}
		} 
		
		// For OF1.0, transport ports are specified using generic tp_src, tp_dst type strings.
		// Once the whole json string has been parsed, find out the IpProto to properly assign the ports.
		// If IpProto not specified, print error, and make sure all TP columns are clear.
		if (ipProto != null && ipProto.equalsIgnoreCase("tcp")) {
			if (tpSrcPort != null) {
				entry.remove(MACTrackerPusher.Columns.COLUMN_TP_SRC);
				entry.put(MACTrackerPusher.matchFieldToColumnName(MatchFields.TCP_SRC), tpSrcPort);
			}
			if (tpDstPort != null) {
				entry.remove(MACTrackerPusher.Columns.COLUMN_TP_DST);
				entry.put(MACTrackerPusher.matchFieldToColumnName(MatchFields.TCP_DST), tpDstPort);
			}
		} else if (ipProto != null && ipProto.equalsIgnoreCase("udp")) {
			if (tpSrcPort != null) {
				entry.remove(MACTrackerPusher.Columns.COLUMN_TP_SRC);
				entry.put(MACTrackerPusher.matchFieldToColumnName(MatchFields.UDP_SRC), tpSrcPort);
			}
			if (tpDstPort != null) {
				entry.remove(MACTrackerPusher.Columns.COLUMN_TP_DST);
				entry.put(MACTrackerPusher.matchFieldToColumnName(MatchFields.UDP_DST), tpDstPort);
			}
		} else if (ipProto != null && ipProto.equalsIgnoreCase("sctp")) {
			if (tpSrcPort != null) {
				entry.remove(MACTrackerPusher.Columns.COLUMN_TP_SRC);
				entry.put(MACTrackerPusher.matchFieldToColumnName(MatchFields.SCTP_SRC), tpSrcPort);
			}
			if (tpDstPort != null) {
				entry.remove(MACTrackerPusher.Columns.COLUMN_TP_DST);
				entry.put(MACTrackerPusher.matchFieldToColumnName(MatchFields.SCTP_DST), tpDstPort);
			}
		} else {
			log.debug("Got IP protocol of '{}' and tp-src of '{}' and tp-dst of '" + tpDstPort + "' via SFP REST API", ipProto, tpSrcPort);
		}

		return entry;
	}   
}