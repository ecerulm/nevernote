/*
 * This file is part of NeverNote 
 * Copyright 2009 Randy Baumgarte
 * 
 * This file may be licensed under the terms of of the
 * GNU General Public License Version 2 (the ``GPL'').
 *
 * Software distributed under the License is distributed
 * on an ``AS IS'' basis, WITHOUT WARRANTY OF ANY KIND, either
 * express or implied. See the GPL for the specific language
 * governing rights and limitations.
 *
 * You should have received a copy of the GPL along with this
 * program. If not, go to http://www.gnu.org/licenses/gpl.html
 * or write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
*/


package cx.fbn.nevernote.sql;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteAttributes;
import com.evernote.edam.type.Resource;
import com.evernote.edam.type.Tag;
import com.trolltech.qt.core.QByteArray;
import com.trolltech.qt.core.QDateTime;
import com.trolltech.qt.core.QTextCodec;

import cx.fbn.nevernote.Global;
import cx.fbn.nevernote.evernote.EnmlConverter;
import cx.fbn.nevernote.sql.driver.NSqlQuery;
import cx.fbn.nevernote.utilities.ApplicationLogger;
import cx.fbn.nevernote.utilities.Pair;

public class NoteTable {
	private final ApplicationLogger 		logger;
	public final NoteTagsTable				noteTagsTable;
	public NoteResourceTable				noteResourceTable;
	private final DatabaseConnection		db;
	int id;

	// Prepared Queries to improve speed
	private NSqlQuery						getQueryWithContent;
	private NSqlQuery						getQueryWithoutContent;
	private NSqlQuery						getAllQueryWithoutContent;
	
	// Constructor
	public NoteTable(ApplicationLogger l, DatabaseConnection d) {
		logger = l;
		db = d;
		id = 0;
		noteResourceTable = new NoteResourceTable(logger, db);
		noteTagsTable = new NoteTagsTable(logger, db);
		getQueryWithContent = null;
		getQueryWithoutContent = null;
		
	}
	// Create the table
	public void createTable() {
		getQueryWithContent = new NSqlQuery(db.getConnection());
		getQueryWithoutContent = new NSqlQuery(db.getConnection());
		NSqlQuery query = new NSqlQuery(db.getConnection());
        logger.log(logger.HIGH, "Creating table Note...");
        if (!query.exec("Create table Note (guid varchar primary key, " +
        		"updateSequenceNumber integer, title varchar, content varchar, contentHash varchar, "+
        		"contentLength integer, created timestamp, updated timestamp, deleted timestamp, " 
        		+"active integer, notebookGuid varchar, attributeSubjectDate timestamp, "+
        		"attributeLatitude double, attributeLongitude double, attributeAltitude double,"+
        		"attributeAuthor varchar, attributeSource varchar, attributeSourceUrl varchar, "+
        		"attributeSourceApplication varchar, indexNeeded boolean, isExpunged boolean, " +
        		"isDirty boolean)"))       		
        	logger.log(logger.HIGH, "Table Note creation FAILED!!!");    
        if (!query.exec("CREATE INDEX unindexed_notess on note (indexneeded desc, guid);"))
        	logger.log(logger.HIGH, "Note unindexed_notes index creation FAILED!!!");
        if (!query.exec("CREATE INDEX unsynchronized_notes on note (isDirty desc, guid);"))
        	logger.log(logger.HIGH, "note unsynchronized_notes index creation FAILED!!!");  
        noteTagsTable.createTable();
        noteResourceTable.createTable();     
	}
	// Drop the table
	public void dropTable() {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		query.exec("Drop table Note");
		noteTagsTable.dropTable();
		noteResourceTable.dropTable();
	}
	// Save Note List from Evernote 
	public void addNote(Note n, boolean isDirty) {
		logger.log(logger.EXTREME, "Inside addNote");
		if (n == null)
			return;
		
		SimpleDateFormat simple = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		NSqlQuery query = new NSqlQuery(db.getConnection());			
		query.prepare("Insert Into Note ("
				+"guid, updateSequenceNumber, title, content, "
				+"contentHash, contentLength, created, updated, deleted, active, notebookGuid, "
				+"attributeSubjectDate, attributeLatitude, attributeLongitude, attributeAltitude, "
				+"attributeAuthor, attributeSource, attributeSourceUrl, attributeSourceApplication, "
				+"indexNeeded, isExpunged, isDirty, titlecolor, thumbnailneeded" 
				+") Values("
				+":guid, :updateSequenceNumber, :title, :content, "
				+":contentHash, :contentLength, :created, :updated, :deleted, :active, :notebookGuid, "
				+":attributeSubjectDate, :attributeLatitude, :attributeLongitude, :attributeAltitude, "
				+":attributeAuthor, :attributeSource, :attributeSourceUrl, :attributeSourceApplication, "
				+":indexNeeded, :isExpunged, :isDirty, -1, true) ");

		StringBuilder created = new StringBuilder(simple.format(n.getCreated()));			
		StringBuilder updated = new StringBuilder(simple.format(n.getUpdated()));			
		StringBuilder deleted = new StringBuilder(simple.format(n.getDeleted()));

		EnmlConverter enml = new EnmlConverter(logger);
		
		query.bindValue(":guid", n.getGuid());
		query.bindValue(":updateSequenceNumber", n.getUpdateSequenceNum());
		query.bindValue(":title", n.getTitle());
		query.bindValue(":content", enml.fixEnXMLCrap(enml.fixEnMediaCrap(n.getContent())));
		query.bindValue(":contentHash", n.getContentHash());
		query.bindValue(":contentLength", n.getContentLength());
		query.bindValue(":created", created.toString());
		query.bindValue(":updated", updated.toString());
		query.bindValue(":deleted", deleted.toString());
		query.bindValue(":active", n.isActive());
		query.bindValue(":notebookGuid", n.getNotebookGuid());
		
		if (n.getAttributes() != null) {
			created = new StringBuilder(simple.format(n.getAttributes().getSubjectDate()));
			query.bindValue(":attributeSubjectDate", created.toString());
			query.bindValue(":attributeLatitude", n.getAttributes().getLatitude());
			query.bindValue(":attributeLongitude", n.getAttributes().getLongitude());
			query.bindValue(":attributeAltitude", n.getAttributes().getAltitude());
			query.bindValue(":attributeAuthor", n.getAttributes().getAuthor());
			query.bindValue(":attributeSource", n.getAttributes().getSource());
			query.bindValue(":attributeSourceUrl", n.getAttributes().getSourceURL());
			query.bindValue(":attributeSourceApplication", n.getAttributes().getSourceApplication());
		}
		query.bindValue(":indexNeeded", true);
		query.bindValue(":isExpunged", false);
		query.bindValue(":isDirty", isDirty);

		
		if (!query.exec())
			logger.log(logger.MEDIUM, query.lastError());
		
		// Save the note tags
		if (n.getTagGuids() != null) {
			for (int i=0; i<n.getTagGuids().size(); i++) 
				noteTagsTable.saveNoteTag(n.getGuid(), n.getTagGuids().get(i));
		}
		logger.log(logger.EXTREME, "Leaving addNote");
	} 
	// Setup queries for get to save time later
	private void prepareQueries() {
		getQueryWithContent = new NSqlQuery(db.getConnection());
		getQueryWithoutContent = new NSqlQuery(db.getConnection());
		getAllQueryWithoutContent = new NSqlQuery(db.getConnection());
		
		if (!getQueryWithContent.prepare("Select "
				+"guid, updateSequenceNumber, title, "
				+"created, updated, deleted, active, notebookGuid, "
				+"attributeSubjectDate, attributeLatitude, attributeLongitude, attributeAltitude, "
				+"attributeAuthor, attributeSource, attributeSourceUrl, attributeSourceApplication, "
				+"content, contentHash, contentLength"
				+" from Note where guid=:guid and isExpunged=false")) {
					logger.log(logger.EXTREME, "Note SQL select prepare with content has failed.");
					logger.log(logger.MEDIUM, getQueryWithContent.lastError());
		}
		
		if (!getQueryWithoutContent.prepare("Select "
				+"guid, updateSequenceNumber, title, "
				+"created, updated, deleted, active, notebookGuid, "
				+"attributeSubjectDate, attributeLatitude, attributeLongitude, attributeAltitude, "
				+"attributeAuthor, attributeSource, attributeSourceUrl, attributeSourceApplication "
				+" from Note where guid=:guid and isExpunged=false")) {
					logger.log(logger.EXTREME, "Note SQL select prepare without content has failed.");
					logger.log(logger.MEDIUM, getQueryWithoutContent.lastError());
		}
		if (!getAllQueryWithoutContent.prepare("Select "
				+"guid, updateSequenceNumber, title, "
				+"created, updated, deleted, active, notebookGuid, "
				+"attributeSubjectDate, attributeLatitude, attributeLongitude, attributeAltitude, "
				+"attributeAuthor, attributeSource, attributeSourceUrl, attributeSourceApplication "
				+" from Note where isExpunged = false")) {
					logger.log(logger.EXTREME, "Note SQL select prepare without content has failed.");
					logger.log(logger.MEDIUM, getQueryWithoutContent.lastError());
		}
	}

	// Get a note's content in raw, binary format for the sync.
	public String getNoteContentBinary(String guid) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		query.prepare("Select content from note where guid=:guid");
		query.bindValue(":guid", guid);
		query.exec();		
		query.next();
		return query.valueString(0);
	}
	// Get a note by Guid
	public Note getNote(String noteGuid, boolean loadContent, boolean loadResources, boolean loadRecognition, boolean loadBinary, boolean loadTags) {
		if (noteGuid == null)
			return null;
		if (noteGuid.trim().equals(""))
			return null;

		prepareQueries();
		NSqlQuery query;
		if (loadContent) {
			query = getQueryWithContent;
		} else {
			query = getQueryWithoutContent;
		}
		
		query.bindValue(":guid", noteGuid);
		if (!query.exec()) {
			logger.log(logger.EXTREME, "Note SQL select exec has failed.");
			logger.log(logger.MEDIUM, query.lastError());
			return null;
		}
		if (!query.next()) {
			logger.log(logger.EXTREME, "SQL Retrieve failed for note guid " +noteGuid + " in getNote()");
			logger.log(logger.EXTREME, " -> " +query.lastError().toString());
			logger.log(logger.EXTREME, " -> " +query.lastError());
			return null;
		}
		Note n = mapNoteFromQuery(query, loadContent, loadResources, loadRecognition, loadBinary, loadTags);
		n.setContent(fixCarriageReturn(n.getContent()));
		return n;
	}
	// Get a note by Guid
	public Note mapNoteFromQuery(NSqlQuery query, boolean loadContent, boolean loadResources, boolean loadRecognition, boolean loadBinary, boolean loadTags) {
		DateFormat indfm = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
//		indfm = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");

		
		Note n = new Note();
		NoteAttributes na = new NoteAttributes();
		n.setAttributes(na);
		
		n.setGuid(query.valueString(0));
		n.setUpdateSequenceNum(new Integer(query.valueString(1)));
		n.setTitle(query.valueString(2));

		try {
			n.setCreated(indfm.parse(query.valueString(3)).getTime());
			n.setUpdated(indfm.parse(query.valueString(4)).getTime());
			n.setDeleted(indfm.parse(query.valueString(5)).getTime());
		} catch (ParseException e) {
			e.printStackTrace();
		}

		n.setActive(query.valueBoolean(6,true));
		n.setNotebookGuid(query.valueString(7));
		
		try {
			String attributeSubjectDate = query.valueString(8);
			if (!attributeSubjectDate.equals(""))
				na.setSubjectDate(indfm.parse(attributeSubjectDate).getTime());
		} catch (ParseException e) {
			e.printStackTrace();
		}
		na.setLatitude(new Float(query.valueString(9)));
		na.setLongitude(new Float(query.valueString(10)));
		na.setAltitude(new Float(query.valueString(11)));
		na.setAuthor(query.valueString(12));
		na.setSource(query.valueString(13));
		na.setSourceURL(query.valueString(14));
		na.setSourceApplication(query.valueString(15));
		
		if (loadTags) {
			n.setTagGuids(noteTagsTable.getNoteTags(n.getGuid()));
			List<String> tagNames = new ArrayList<String>();
			TagTable tagTable = new TagTable(logger, db);
			for (int i=0; i<n.getTagGuids().size(); i++) {
				String currentGuid = n.getTagGuids().get(i);
				Tag tag = tagTable.getTag(currentGuid);
				tagNames.add(tag.getName());
			}
			n.setTagNames(tagNames);
		}
		
		if (loadContent) {
						
			QTextCodec codec = QTextCodec.codecForLocale();
			codec = QTextCodec.codecForName("UTF-8");
	        String unicode =  codec.fromUnicode(query.valueString(16)).toString();
			n.setContent(unicode);
//			n.setContent(query.valueString(16).toString());
			
			String contentHash = query.valueString(17);
			if (contentHash != null)
				n.setContentHash(contentHash.getBytes());
			n.setContentLength(new Integer(query.valueString(18)));
		}
		if (loadResources)
			n.setResources(noteResourceTable.getNoteResources(n.getGuid(), loadBinary));
		if (loadRecognition) {
			if (n.getResources() == null) {
				List<Resource> resources = noteResourceTable.getNoteResourcesRecognition(n.getGuid());
				n.setResources(resources);
			} else {
				// We need to merge the recognition resources with the note resources retrieved earlier
				for (int i=0; i<n.getResources().size(); i++) {
					Resource r = noteResourceTable.getNoteResourceRecognition(n.getResources().get(i).getGuid());
					n.getResources().get(i).setRecognition(r.getRecognition());
				}
			}
		}
		n.setContent(fixCarriageReturn(n.getContent()));
		return n;
	}
	// Update a note's title
	public void updateNoteTitle(String guid, String title) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		boolean check = query.prepare("Update Note set title=:title, isDirty=true where guid=:guid");
		if (!check) {
			logger.log(logger.EXTREME, "Update note title sql prepare has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		query.bindValue(":title", title);
		query.bindValue(":guid", guid);
		check = query.exec();
		if (!check) {
			logger.log(logger.EXTREME, "Update note title has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
	}
	// Update a note's creation date
	public void updateNoteCreatedDate(String guid, QDateTime date) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		boolean check = query.prepare("Update Note set created=:created, isDirty=true where guid=:guid");
		if (!check) {
			logger.log(logger.EXTREME, "Update note creation update sql prepare has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		
		query.bindValue(":created", date.toString("yyyy-MM-dd HH:mm:ss"));
		query.bindValue(":guid", guid);
		
		check = query.exec();
		if (!check) {
			logger.log(logger.EXTREME, "Update note creation date has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
	}
	// Update a note's creation date
	public void updateNoteAlteredDate(String guid, QDateTime date) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		boolean check = query.prepare("Update Note set updated=:altered, isDirty=true where guid=:guid");
		if (!check) {
			logger.log(logger.EXTREME, "Update note altered sql prepare has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		
		query.bindValue(":altered", date.toString("yyyy-MM-dd HH:mm:ss"));
		query.bindValue(":guid", guid);
		
		check = query.exec();
		if (!check) {
			logger.log(logger.EXTREME, "Update note altered date has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
	}
	// Update a note's creation date
	public void updateNoteSubjectDate(String guid, QDateTime date) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		boolean check = query.prepare("Update Note set attributeSubjectDate=:altered, isDirty=true where guid=:guid");
		if (!check) {
			logger.log(logger.EXTREME, "Update note subject date sql prepare has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
	
		query.bindValue(":altered", date.toString("yyyy-MM-dd HH:mm:ss"));
		query.bindValue(":guid", guid);
		
		check = query.exec();
		if (!check) {
			logger.log(logger.EXTREME, "Update note subject date date has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
	}
	// Update a note's creation date
	public void updateNoteAuthor(String guid, String author) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		boolean check = query.prepare("Update Note set attributeAuthor=:author, isDirty=true where guid=:guid");
		if (!check) {
			logger.log(logger.EXTREME, "Update note author sql prepare has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}

		query.bindValue(":author", author);
		query.bindValue(":guid", guid);

		check = query.exec();
		if (!check) {
			logger.log(logger.EXTREME, "Update note author has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		
	}
	// Update a note's geo tags
	public void updateNoteGeoTags(String guid, Double lon, Double lat, Double alt) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		boolean check = query.prepare("Update Note set attributeLongitude=:longitude, "+
				"attributeLatitude=:latitude, attributeAltitude=:altitude, isDirty=true where guid=:guid");
		if (!check) {
			logger.log(logger.EXTREME, "Update note author sql prepare has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}

		query.bindValue(":longitude", lon);
		query.bindValue(":latitude", lat);
		query.bindValue(":altitude", alt);
		query.bindValue(":guid", guid);

		check = query.exec();
		if (!check) {
			logger.log(logger.EXTREME, "Update note geo tag has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		
	}
	// Update a note's creation date
	public void updateNoteSourceUrl(String guid, String url) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		boolean check = query.prepare("Update Note set attributeSourceUrl=:url, isDirty=true where guid=:guid");
		if (!check) {
			logger.log(logger.EXTREME, "Update note url sql prepare has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		
		query.bindValue(":url", url);
		query.bindValue(":guid", guid);

		check = query.exec();
		if (!check) {
			logger.log(logger.EXTREME, "Update note url has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		
	}
	// Update the notebook that a note is assigned to
	public void updateNoteNotebook(String guid, String notebookGuid, boolean expungeFromRemote) {
		String currentNotebookGuid = new String("");
		
		
		// If we are going from a synchronized notebook to a local notebook, we
		// need to tell Evernote to purge the note online.  However, if this is  
		// conflicting change we move it to the local notebook without deleting it 
		// or it would then delete the copy on the remote server.
		NotebookTable notebookTable = new NotebookTable(logger, db);
		DeletedTable deletedTable = new DeletedTable(logger, db);
		if (expungeFromRemote) {
			if (!notebookTable.isNotebookLocal(currentNotebookGuid) & notebookTable.isNotebookLocal(notebookGuid)) {
				deletedTable.addDeletedItem(guid, "NOTE");
			}
		}
		
		NSqlQuery query = new NSqlQuery(db.getConnection());
		boolean check = query.prepare("Update Note set notebookGuid=:notebook, isDirty=true where guid=:guid");
		if (!check) {
			logger.log(logger.EXTREME, "Update note notebook sql prepare has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		query.bindValue(":notebook", notebookGuid);
		query.bindValue(":guid", guid);
		
		check = query.exec();
		if (!check) {
			logger.log(logger.EXTREME, "Update note notebook has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		};
	}
	// Update a note's title
	public void updateNoteContent(String guid, String content) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		boolean check = query.prepare("Update Note set content=:content, updated=CURRENT_TIMESTAMP(), isDirty=true, indexNeeded=true " +
				" where guid=:guid");
		if (!check) {
			logger.log(logger.EXTREME, "Update note content sql prepare has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		
		query.bindValue(":content", content);
		query.bindValue(":guid", guid);

		check = query.exec();
		if (!check) {
			logger.log(logger.EXTREME, "Update note content has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
	}

	
	// Check a note to see if it passes the attribute selection criteria
	public boolean checkAttributeSelection(Note n) {
		if (Global.createdSinceFilter.check(n) &&
			Global.createdBeforeFilter.check(n) && 
			Global.changedSinceFilter.check(n) &&
			Global.changedBeforeFilter.check(n) )
				return true;
		
		return false;
	}
	// Delete a note
	public void deleteNote(String guid) {
        NSqlQuery query = new NSqlQuery(db.getConnection());
        query.prepare("Update Note set deleted=CURRENT_TIMESTAMP(), active=false, isDirty=true where guid=:guid");
		query.bindValue(":guid", guid);
		if (!query.exec()) {
			logger.log(logger.MEDIUM, "Note delete failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
	}
	public void restoreNote(String guid) {
        NSqlQuery query = new NSqlQuery(db.getConnection());
		query.prepare("Update Note set deleted='1969-12-31 19.00.00', active=true, isDirty=true where guid=:guid");
//		query.prepare("Update Note set deleted=0, active=true, isDirty=true where guid=:guid");
		query.bindValue(":guid", guid);
		if (!query.exec()) {
			logger.log(logger.MEDIUM, "Note restore failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
	}
	// Purge a note (actually delete it instead of just marking it deleted)
	public void expungeNote(String guid, boolean permanentExpunge, boolean needsSync) {
		
		if (!permanentExpunge) {
			hideExpungedNote(guid, needsSync);
			return;
		}
		
		
        NSqlQuery note = new NSqlQuery(db.getConnection());
        NSqlQuery resources = new NSqlQuery(db.getConnection());
        NSqlQuery tags = new NSqlQuery(db.getConnection());
        NSqlQuery words = new NSqlQuery(db.getConnection());
        
       	note.prepare("Delete from Note where guid=:guid");
		resources.prepare("Delete from NoteResources where noteGuid=:guid");
		tags.prepare("Delete from NoteTags where noteGuid=:guid");
		words.prepare("Delete from words where guid=:guid");

		note.bindValue(":guid", guid);
		resources.bindValue(":guid", guid);
		tags.bindValue(":guid", guid);
		words.bindValue(":guid", guid);
	
		// Start purging notes.
		if (!note.exec()) {
			logger.log(logger.MEDIUM, "Purge from note failed.");
			logger.log(logger.MEDIUM, note.lastError());
		}
		if (!resources.exec()) {
				logger.log(logger.MEDIUM, "Purge from resources failed.");
			logger.log(logger.MEDIUM, resources.lastError());
		}
		if (!tags.exec()) {
			logger.log(logger.MEDIUM, "Note tags delete failed.");
			logger.log(logger.MEDIUM, tags.lastError());
		}
		if (!words.exec()) {
			logger.log(logger.MEDIUM, "Word delete failed.");
			logger.log(logger.MEDIUM, words.lastError());
		}
		if (needsSync) {
			DeletedTable deletedTable = new DeletedTable(logger, db);
			deletedTable.addDeletedItem(guid, "Note");
		}

	}
	// Purge a note (actually delete it instead of just marking it deleted)
	public void hideExpungedNote(String guid, boolean needsSync) {
        NSqlQuery note = new NSqlQuery(db.getConnection());
        NSqlQuery resources = new NSqlQuery(db.getConnection());
        NSqlQuery tags = new NSqlQuery(db.getConnection());
        NSqlQuery words = new NSqlQuery(db.getConnection());
        
       	note.prepare("Update Note set isExpunged=true where guid=:guid");
		resources.prepare("Delete from NoteResources where noteGuid=:guid");
		tags.prepare("Delete from NoteTags where noteGuid=:guid");
		words.prepare("Delete from words where guid=:guid");

		note.bindValue(":guid", guid);
		resources.bindValue(":guid", guid);
		tags.bindValue(":guid", guid);
		words.bindValue(":guid", guid);

		// Start purging notes.
		if (!note.exec()) {
			logger.log(logger.MEDIUM, "Purge from note failed.");
			logger.log(logger.MEDIUM, note.lastError());
		}
		if (!resources.exec()) {
				logger.log(logger.MEDIUM, "Purge from resources failed.");
			logger.log(logger.MEDIUM, resources.lastError());
		}
		if (!tags.exec()) {
			logger.log(logger.MEDIUM, "Note tags delete failed.");
			logger.log(logger.MEDIUM, tags.lastError());
		}
		if (!words.exec()) {
			logger.log(logger.MEDIUM, "Word delete failed.");
			logger.log(logger.MEDIUM, words.lastError());
		}
		if (needsSync) {
			DeletedTable deletedTable = new DeletedTable(logger, db);
			deletedTable.addDeletedItem(guid, "Note");
		}
	}

		
	// Purge all deleted notes;
	public void expungeAllDeletedNotes() {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		query.exec("select guid, updateSequenceNumber from note where active = false");
		while (query.next()) {
			String guid = query.valueString(0);
			Integer usn = new Integer(query.valueString(1));
			if (usn == 0)
				expungeNote(guid, true, false);
			else
				expungeNote(guid, false, true);
		}
	}
	// Update the note sequence number
	public void updateNoteSequence(String guid, int sequence) {
		boolean check;
        NSqlQuery query = new NSqlQuery(db.getConnection());
		check = query.prepare("Update Note set updateSequenceNumber=:sequence where guid=:guid");

		query.bindValue(":sequence", sequence);
		query.bindValue(":guid", guid);
		
		query.exec();
		if (!check) {
			logger.log(logger.MEDIUM, "Note sequence update failed.");
			logger.log(logger.MEDIUM, query.lastError());
		} 
	}
	// Update the note Guid
	public void updateNoteGuid(String oldGuid, String newGuid) {
		boolean check;
        NSqlQuery query = new NSqlQuery(db.getConnection());
		query.prepare("Update Note set guid=:newGuid where guid=:oldGuid");

		query.bindValue(":newGuid", newGuid);
		query.bindValue(":oldGuid", oldGuid);

		check = query.exec();
		if (!check) {
			logger.log(logger.MEDIUM, "Note Guid update failed.");
			logger.log(logger.MEDIUM, query.lastError());
		} 
		
		query.prepare("Update NoteTags set noteGuid=:newGuid where noteGuid=:oldGuid");
		query.bindValue(":newGuid", newGuid);
		query.bindValue(":oldGuid", oldGuid);
		check = query.exec();
		if (!check) {
			logger.log(logger.MEDIUM, "Note guid update failed for NoteTags.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		
		query.prepare("Update words set guid=:newGuid where guid=:oldGuid");
		query.bindValue(":newGuid", newGuid);
		query.bindValue(":oldGuid", oldGuid);
		query.exec();
		if (!check) {
			logger.log(logger.MEDIUM, "Note guid update failed for Words.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		query.prepare("Update noteresources set noteguid=:newGuid where noteguid=:oldGuid");
		query.bindValue(":newGuid", newGuid);
		query.bindValue(":oldGuid", oldGuid);
		query.exec();
		if (!check) {
			logger.log(logger.MEDIUM, "Note guid update failed for noteresources.");
			logger.log(logger.MEDIUM, query.lastError());
		}
	}
	// Update a note
	public void updateNote(Note n, boolean isNew) {
		boolean isExpunged = isNoteExpunged(n.getGuid());
		
		expungeNote(n.getGuid(), !isExpunged, false);
		addNote(n, false);
	}
	// Does a note exist?
	public boolean exists(String guid) {
 		if (guid == null)
 			return false;
		if (guid.trim().equals(""))
			return false;
 		NSqlQuery query = new NSqlQuery(db.getConnection());
		query.prepare("Select guid from note where guid=:guid");
		query.bindValue(":guid", guid);
		if (!query.exec())
			logger.log(logger.EXTREME, "note.exists SQL retrieve has failed.");
		boolean retVal = query.next();
		return retVal;
	}
	// Does a note exist?
	public boolean isNoteExpunged(String guid) {
 		if (guid == null)
 			return false;
		if (guid.trim().equals(""))
			return false;
 		NSqlQuery query = new NSqlQuery(db.getConnection());
		query.prepare("Select isExpunged from note where guid=:guid and isExpunged = true");
		query.bindValue(":guid", guid);
		if (!query.exec())
			logger.log(logger.EXTREME, "note.isNoteExpunged SQL retrieve has failed.");
		boolean retVal = query.next();
		return retVal;
	}
	// This is a convience method to check if a tag exists & update/create based upon it
	public void syncNote(Note tag, boolean isDirty) {
		if (exists(tag.getGuid()))
			updateNote(tag, isDirty);
		else
			addNote(tag, isDirty);
	}
	// Get a list of notes that need to be updated
	public List <Note> getDirty() {
		String guid;
		Note tempNote;
		List<Note> notes = new ArrayList<Note>();
		List<String> index = new ArrayList<String>();
		
		boolean check;			
        NSqlQuery query = new NSqlQuery(db.getConnection());
        				
		check = query.exec("Select guid from Note where isDirty = true and isExpunged = false and notebookGuid not in (select guid from notebook where local = true)");
		if (!check) 
			logger.log(logger.EXTREME, "Note SQL retrieve has failed: " +query.lastError().toString());
		
		// Get a list of the notes
		while (query.next()) {
			guid = new String();
			guid = query.valueString(0);
			index.add(guid); 
		}	
		
		// Start getting notes
		for (int i=0; i<index.size(); i++) {
			tempNote = getNote(index.get(i), true,true,false,true,true);
			notes.add(tempNote);
		}
		return notes;	
	}
	// Get a list of notes that need to be updated
	public boolean isNoteDirty(String guid) {
		
		boolean check;			
        NSqlQuery query = new NSqlQuery(db.getConnection());
        				
		check = query.prepare("Select guid from Note where isDirty = true and guid=:guid");
		query.bindValue(":guid", guid);
		check = query.exec();
		if (!check) 
			logger.log(logger.EXTREME, "Note SQL retrieve has failed: " +query.lastError().toString());
		
		boolean returnValue;
		// Get a list of the notes
		if (query.next()) 
			returnValue = true; 
		else
			returnValue = false;

		return returnValue;	
	}
	// Get a list of notes that need to be updated
	public List <String> getUnsynchronizedGUIDs() {
		String guid;
		List<String> index = new ArrayList<String>();
		
		boolean check;			
        NSqlQuery query = new NSqlQuery(db.getConnection());
        				
		check = query.exec("Select guid from Note where isDirty = true");
		if (!check) 
			logger.log(logger.EXTREME, "Note SQL retrieve has failed: " +query.lastError().toString());
		
		// Get a list of the notes
		while (query.next()) {
			guid = new String();
			guid = query.valueString(0);
			index.add(guid); 
		}	
		return index;	
	}
	// Reset the dirty bit
	public void  resetDirtyFlag(String guid) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		
		query.prepare("Update note set isdirty=false where guid=:guid");
		query.bindValue(":guid", guid);
		if (!query.exec())
			logger.log(logger.EXTREME, "Error resetting note dirty field.");
	}
	// Get all notes
	public List<String> getAllGuids() {
		List<String> notes = new ArrayList<String>();
		
		boolean check;					
        NSqlQuery query = new NSqlQuery(db.getConnection());
        				
		check = query.exec("Select guid from Note");
		if (!check)
			logger.log(logger.EXTREME, "Notebook SQL retrieve has failed: "+query.lastError());

		// Get a list of the notes
		while (query.next()) {
			notes.add(new String(query.valueString(0))); 
		}
		return notes;
	}
	// Get all notes
	public List<Note> getAllNotes() {
		List<Note> notes = new ArrayList<Note>();
		prepareQueries();
		boolean check;					
        NSqlQuery query = getAllQueryWithoutContent;
		check = query.exec();
		if (!check)
			logger.log(logger.EXTREME, "Notebook SQL retrieve has failed: "+query.lastError());
		// Get a list of the notes
		while (query.next()) {
			notes.add(mapNoteFromQuery(query, false, false, false, false, true));
		}
		return notes;
	}
	// Count unindexed notes
	public int getUnindexedCount() {
        NSqlQuery query = new NSqlQuery(db.getConnection());
		query.exec("select count(*) from note where indexneeded=true and isExpunged = false");
		query.next(); 
		int returnValue = new Integer(query.valueString(0));
		return returnValue;
	}
	// Count unsynchronized notes
	public int getDirtyCount() {
        NSqlQuery query = new NSqlQuery(db.getConnection());
		query.exec("select count(*) from note where isDirty=true and isExpunged = false");
		query.next(); 
		int returnValue = new Integer(query.valueString(0));
		return returnValue;
	}
	// Count notes
	public int getNoteCount() {
        NSqlQuery query = new NSqlQuery(db.getConnection());
		query.exec("select count(*) from note where isExpunged = false");
		query.next(); 
		int returnValue = new Integer(query.valueString(0));
		return returnValue;
	}
	// Count deleted notes
	public int getDeletedCount() {
        NSqlQuery query = new NSqlQuery(db.getConnection());
		query.exec("select count(*) from note where isExpunged = false and active = false");
		if (!query.next()) 
			return 0;
		int returnValue = new Integer(query.valueString(0));
		return returnValue;
	}
	// Reset a note sequence number to zero.  This is useful for moving conflicting notes
	public void resetNoteSequence(String guid) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		boolean check = query.prepare("Update Note set updateSequenceNumber=0, isDirty=true where guid=:guid");
		if (!check) {
			logger.log(logger.EXTREME, "Update note ResetSequence sql prepare has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
		query.bindValue(":guid", guid);
		check = query.exec();
		if (!check) {
			logger.log(logger.EXTREME, "Update note sequence number has failed.");
			logger.log(logger.MEDIUM, query.lastError());
		}
	}
	
	
	// Update a note resource by the hash
	public void updateNoteResourceGuidbyHash(String noteGuid, String resGuid, String hash) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
/*		query.prepare("Select guid from NoteResources where noteGuid=:noteGuid and datahash=:hex");
		query.bindValue(":noteGuid", noteGuid);
		query.bindValue(":hex", hash);
		query.exec();
		if (!query.next()) {
			logger.log(logger.LOW, "Error finding note resource in RNoteTable.updateNoteResourceGuidbyHash.  GUID="+noteGuid +" resGuid="+ resGuid+" hash="+hash);
			return;
		}
		String guid = query.valueString(0);
*/		
		query.prepare("update noteresources set guid=:guid where noteGuid=:noteGuid and datahash=:hex");
		query.bindValue(":guid", resGuid);
		query.bindValue(":noteGuid", noteGuid);
		query.bindValue(":hex", hash);
		if (!query.exec()) {
			logger.log(logger.EXTREME, "Note Resource Update by Hash failed");
			logger.log(logger.EXTREME, query.lastError().toString());
		}
	}

	// Fix CRLF problem that is on some notes
	private String fixCarriageReturn(String note) {
		if (note == null || !Global.enableCarriageReturnFix)
			return note;
		QByteArray a0Hex = new QByteArray("a0");
		String a0 = QByteArray.fromHex(a0Hex).toString();
		note = note.replace("<div>"+a0+"</div>", "<div>&nbsp;</div>");
		return note.replace("<div/>", "<div>&nbsp;</div>");
	}
	
	
	
	//********************************************************************************
	//********************************************************************************
	//* Indexing Functions
	//********************************************************************************
	//********************************************************************************
	// set/unset a note to be reindexed
	public void setIndexNeeded(String guid, Boolean flag) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		query.prepare("Update Note set indexNeeded=:flag where guid=:guid");

		if (flag)
			query.bindValue(":flag", 1);
		else
			query.bindValue(":flag", 0);
		query.bindValue(":guid", guid);
		if (!query.exec()) {
			logger.log(logger.MEDIUM, "Note indexNeeded update failed.");
			logger.log(logger.MEDIUM, query.lastError());
		} 
	}
	// Set all notes to be reindexed
	public void reindexAllNotes() {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		if (!query.exec("Update Note set indexNeeded=true")) {
			logger.log(logger.MEDIUM, "Note reindexAllNotes update failed.");
			logger.log(logger.MEDIUM, query.lastError());
		} 
	}

	// Get all unindexed notes
	public List <String> getUnindexed() {
		String guid;
		List<String> index = new ArrayList<String>();
        NSqlQuery query = new NSqlQuery(db.getConnection());
        				
		if (!query.exec("Select guid from Note where isExpunged = false and indexNeeded = true and DATEDIFF('MINUTE',updated,CURRENT_TIMESTAMP)>5"))
			logger.log(logger.EXTREME, "Note SQL retrieve has failed on getUnindexed().");

		// Get a list of the notes
		while (query.next()) {
			guid = new String();
			guid = query.valueString(0);
			index.add(guid); 
		}	
		return index;	
	}
	public List<String> getNextUnindexed(int limit) {
		List<String> guids = new ArrayList<String>();
			
        NSqlQuery query = new NSqlQuery(db.getConnection());
        				
		if (!query.exec("Select guid from Note where isExpunged = false and indexNeeded = true and DATEDIFF('MINUTE',Updated,CURRENT_TIMESTAMP)>5 limit " +limit))
			logger.log(logger.EXTREME, "Note SQL retrieve has failed on getUnindexed().");
		
		// Get a list of the notes
		String guid;
		while (query.next()) {
			guid = new String();
			guid = query.valueString(0);
			guids.add(guid);
		}	
		return guids;	
	}
	
	
	//**********************************************************************************
	//* Title color functions
	//**********************************************************************************
	// Get the title color of all notes
	public List<Pair<String, Integer>> getNoteTitleColors() {
		List<Pair<String,Integer>> returnValue = new ArrayList<Pair<String,Integer>>();
        NSqlQuery query = new NSqlQuery(db.getConnection());
		
		if (!query.exec("Select guid,titleColor from Note where titleColor != -1"))
			logger.log(logger.EXTREME, "Note SQL retrieve has failed on getUnindexed().");

		String guid;
		Integer color;
		
		// Get a list of the notes
		while (query.next()) {
			Pair<String, Integer> pair = new Pair<String,Integer>();
			guid = query.valueString(0);
			color = query.valueInteger(1);
			pair.setFirst(guid);
			pair.setSecond(color);
			returnValue.add(pair); 
		}	

		
		
		return returnValue;
	}
	// Set a title color
	// Reset the dirty bit
	public void  setNoteTitleColor(String guid, int color) {
		NSqlQuery query = new NSqlQuery(db.getConnection());
		
		query.prepare("Update note set titlecolor=:color where guid=:guid");
		query.bindValue(":guid", guid);
		query.bindValue(":color", color);
		if (!query.exec())
			logger.log(logger.EXTREME, "Error updating title color.");
	}

	
	
	//**********************************************************************************
	//* Thumbnail functions
	//**********************************************************************************
	// Set if a new thumbnail is needed
	public void setThumbnailNeeded(String guid, boolean needed) {
		
		boolean check;			
        NSqlQuery query = new NSqlQuery(db.getConnection());
        				
		check = query.prepare("Update note set thumbnailneeded = :needed where guid=:guid");
		query.bindValue(":guid", guid);
		query.bindValue(":needed", needed);
		check = query.exec();
		if (!check) 
			logger.log(logger.EXTREME, "Note SQL set thumbail needed failed: " +query.lastError().toString());

	}
	// Is a thumbail needed for this guid?
	public boolean isThumbnailNeeded(String guid) {
		
		boolean check;			
        NSqlQuery query = new NSqlQuery(db.getConnection());
        				
		check = query.prepare("select thumbnailneeded from note where guid=:guid");
		query.bindValue(":guid", guid);
		check = query.exec();
		if (!check) 
			logger.log(logger.EXTREME, "Note SQL isThumbnailNeeded query failed: " +query.lastError().toString());
		
		boolean returnValue;
		// Get a list of the notes
		if (query.next()) 
			returnValue = query.valueBoolean(0, false); 
		else
			returnValue = false;

		return returnValue;	
	}
	// Set if a new thumbnail is needed
	public void setThumbnail(String guid, QByteArray thumbnail) {
		
		boolean check;			
        NSqlQuery query = new NSqlQuery(db.getConnection());
        				
		check = query.prepare("Update note set thumbnail = :thumbnail where guid=:guid");
		query.bindValue(":guid", guid);
		query.bindValue(":thumbnail", thumbnail.toByteArray());
		check = query.exec();
		if (!check) 
			logger.log(logger.EXTREME, "Note SQL set thumbail failed: " +query.lastError().toString());

	}
	// Set if a new thumbnail is needed
	public QByteArray getThumbnail(String guid) {
		
		boolean check;			
        NSqlQuery query = new NSqlQuery(db.getConnection());
        				
		check = query.prepare("Select thumbnail from note where guid=:guid");
		query.bindValue(":guid", guid);
		check = query.exec();
		if (!check) 
			logger.log(logger.EXTREME, "Note SQL get thumbail failed: " +query.lastError().toString());
		// Get a list of the notes
		if (query.next()) 
			if (query.getBlob(0) != null)
				return new QByteArray(query.getBlob(0)); 
		return null;
	}
	
	
	// Update a note content's hash.  This happens if a resource is edited outside of NN
	public void updateResourceContentHash(String guid, String oldHash, String newHash) {
		Note n = getNote(guid, true, false, false, false,false);
		int position = n.getContent().indexOf("<en-media");
		int endPos;
		for (;position>-1;) {
			endPos = n.getContent().indexOf(">", position+1);
			String oldSegment = n.getContent().substring(position,endPos);
			int hashPos = oldSegment.indexOf("hash=\"");
			int hashEnd = oldSegment.indexOf("\"", hashPos+7);
			String hash = oldSegment.substring(hashPos+6, hashEnd);
			if (hash.equalsIgnoreCase(oldHash)) {
				String newSegment = oldSegment.replace(oldHash, newHash);
				String content = n.getContent().substring(0,position) +
				                 newSegment +
				                 n.getContent().substring(endPos);
				NSqlQuery query = new NSqlQuery(db.getConnection());
				query.prepare("update note set isdirty=true, content=:content where guid=:guid");
				query.bindValue(":content", content);
				query.bindValue(":guid", n.getGuid());
				query.exec();
			}
			
			position = n.getContent().indexOf("<en-media", position+1);
		}
	}
}	
