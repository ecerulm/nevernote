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

package cx.fbn.nevernote.dialog;

import java.util.List;

import com.trolltech.qt.gui.QCheckBox;
import com.trolltech.qt.gui.QComboBox;
import com.trolltech.qt.gui.QFormLayout;
import com.trolltech.qt.gui.QGroupBox;
import com.trolltech.qt.gui.QLabel;
import com.trolltech.qt.gui.QLineEdit;
import com.trolltech.qt.gui.QVBoxLayout;
import com.trolltech.qt.gui.QWidget;

import cx.fbn.nevernote.Global;
import cx.fbn.nevernote.utilities.SyncTimes;

public class ConfigConnectionPage extends QWidget {
	private final QLineEdit useridEdit;
	private final QLineEdit passwordEdit;
	private final QCheckBox rememberPassword;
	private final QCheckBox autoLogin;
	private final QComboBox syncInterval;
	private final SyncTimes syncTimes;
	private final QCheckBox	synchronizeOnClose;
	private final QCheckBox	synchronizeDeletedContents;
	
	public ConfigConnectionPage(QWidget parent) {
		
		// Userid settings
		QGroupBox useridGroup = new QGroupBox(tr("Connection"));
		QLabel useridLabel = new QLabel(tr("Userid"));
		QLabel passwordLabel = new QLabel(tr("Password"));

		
		useridEdit = new QLineEdit();
		useridEdit.setText(Global.username);
		
		passwordEdit = new QLineEdit();
		passwordEdit.setText(Global.password);
		passwordEdit.setEchoMode(QLineEdit.EchoMode.Password);
		
		syncInterval = new QComboBox(this);
		syncTimes = new SyncTimes();
		syncInterval.addItems(syncTimes.stringValues());
		
		rememberPassword = new QCheckBox("Remember Userid & Password");
		autoLogin = new QCheckBox("Automatic Connect");
		synchronizeDeletedContents = new QCheckBox("Synchronze Deleted Note Content");
		synchronizeOnClose = new QCheckBox("Synchronize On Shutdown (only if connected)");
		
		
		QFormLayout useridLayout = new QFormLayout();
		useridLayout.addWidget(useridLabel);
		useridLayout.addWidget(useridEdit);		
		useridLayout.addWidget(passwordLabel);
		useridLayout.addWidget(passwordEdit);
		useridLayout.addWidget(new QLabel(tr("Syncronization Interval")));
		useridLayout.addWidget(syncInterval);
		useridLayout.addWidget(rememberPassword);
		useridLayout.addWidget(autoLogin);
		useridLayout.addWidget(synchronizeOnClose);
		useridLayout.addWidget(synchronizeDeletedContents);
				
		useridGroup.setLayout(useridLayout);
		
		// Add everything together
		QVBoxLayout mainLayout = new QVBoxLayout();
		mainLayout.addWidget(useridGroup);
		mainLayout.addStretch(1);
		setLayout(mainLayout);
		
	}

	
	//*****************************************
	//* Userid get/set methods 
	//*****************************************
	public void setUserid(String id) {
		useridEdit.setText(id);
	}
	public String getUserid() {
		return useridEdit.text();
	}
	

	//*****************************************
	//* Password get/set methods 
	//*****************************************
	public void setPassword(String id) {
		passwordEdit.setText(id);
	}
	public String getPassword() {
		return passwordEdit.text();
	}
	

	//*******************************************
	//* Remember Password get/set
	//*******************************************
	public void setRememberPassword(boolean val) {
		rememberPassword.setChecked(val);
	}
	public boolean getRememberPassword() {
		return rememberPassword.isChecked();
	}
	
	
	
	
	//*******************************************
	//* Automatic login get/set
	//*******************************************
	public void setAutomaticLogin(boolean val) {
		autoLogin.setChecked(val);
	}
	public boolean getAutomaticLogin() {
		return autoLogin.isChecked();
	}

	

	//*****************************************
	//* Synchronize Deleted Note Content
	//*****************************************
	public void setSyncronizeDeletedContent(boolean val) {
		synchronizeDeletedContents.setChecked(val);
	}
	public boolean getSynchronizeDeletedContent() {
		return synchronizeDeletedContents.isChecked();
	}
	

	

	
	
	
	//*****************************************
	//* Get/set synchronize on close
	//*****************************************
	public boolean getSynchronizeOnClose() {
		return synchronizeOnClose.isChecked();
	}
	public void setSynchronizeOnClose(boolean val) {
		synchronizeOnClose.setChecked(val);
	}
	
	
	//*****************************************
	//* Get/set sync intervals
	//*****************************************
	public String getSyncInterval() {
		int i = syncInterval.currentIndex();
		return syncInterval.itemText(i);	
	}
	public void setSyncInterval(String s) {
		List<String> vals = syncTimes.stringValues();
		for (int i=0; i<vals.size(); i++) {
			if (vals.get(i).equalsIgnoreCase(s))
				syncInterval.setCurrentIndex(i);
		}
	}
}
