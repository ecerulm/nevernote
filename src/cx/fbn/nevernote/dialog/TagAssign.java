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

import java.util.ArrayList;
import java.util.List;

import com.evernote.edam.type.Tag;
import com.trolltech.qt.gui.QAbstractItemView;
import com.trolltech.qt.gui.QDialog;
import com.trolltech.qt.gui.QHBoxLayout;
import com.trolltech.qt.gui.QLineEdit;
import com.trolltech.qt.gui.QListWidget;
import com.trolltech.qt.gui.QListWidgetItem;
import com.trolltech.qt.gui.QPushButton;
import com.trolltech.qt.gui.QVBoxLayout;

public class TagAssign extends QDialog {
	private final QListWidget 		tagList;
	private final QPushButton		okButton;
	private final QPushButton		cancelButton;
	private final QLineEdit			newTag;
	private final QPushButton		newTagButton;
	private boolean					okClicked;
	private final List<String> 		tags;
	
	public TagAssign(List<Tag> allTags, List<String> selectedTags) {
		okClicked = false;
		tags = new ArrayList<String>();
		
		tagList = new QListWidget();
		tagList.setSortingEnabled(true);
		tagList.setSelectionMode(QAbstractItemView.SelectionMode.MultiSelection);
		
		newTag = new QLineEdit();
		newTag.textChanged.connect(this, "newTagTextChanged()");
		newTagButton = new QPushButton("Add");
		newTagButton.setEnabled(false);
		newTagButton.clicked.connect(this, "addTag()");
		
		QHBoxLayout addLayout = new QHBoxLayout();
		addLayout.addWidget(newTag);
		addLayout.setStretch(0, 10);
		addLayout.addWidget(newTagButton);
		
		okButton = new QPushButton();
		okButton.setText("OK");
		okButton.pressed.connect(this, "onClicked()");
		
		cancelButton = new QPushButton();
		cancelButton.setText("Cancel");
		cancelButton.pressed.connect(this, "onCancel()");
		
		QHBoxLayout horizontalLayout = new QHBoxLayout();
		horizontalLayout.addWidget(tagList);
		
		QHBoxLayout buttonLayout = new QHBoxLayout();
		buttonLayout.addStretch(1);
		buttonLayout.addWidget(okButton);
		buttonLayout.addWidget(cancelButton);
		setWindowTitle(tr("Note Tags"));	
		
		QVBoxLayout mainLayout = new QVBoxLayout();
		mainLayout.addLayout(horizontalLayout);
		mainLayout.addLayout(addLayout);
		//mainLayout.addStretch(1);
		mainLayout.addSpacing(1);
		mainLayout.addLayout(buttonLayout);
		setLayout(mainLayout);
		
		if (allTags != null) {
			for (int i=0; i<allTags.size(); i++) {
				tags.add(allTags.get(i).getName());
//				tagList.addItem(allTags.get(i).getName());
				QListWidgetItem item = new QListWidgetItem(allTags.get(i).getName());
				tagList.addItem(item);
				if (selectedTags != null) {
					for (int j=0; j<selectedTags.size(); j++) {
						String name = selectedTags.get(j);
						if (name.equals(item.text())) {
							item.setSelected(true);
							j=selectedTags.size()+1;
						}
					}
				}
			}
		}
		
		
	}
	
	@SuppressWarnings("unused")
	private void onClicked() {
		okClicked = true;
		close();
	}
	
	@SuppressWarnings("unused")
	private void onCancel() {
		okClicked = false;
		close();
	}
	
	public boolean okClicked() {
		return okClicked;
	}
	
	@SuppressWarnings("unused")
	private void newTagTextChanged() {
		if (newTag.text().equals("")) {
			newTagButton.setEnabled(false);
			return;
		}
		newTagButton.setEnabled(true);
		for (int i=0; i<tags.size(); i++) {
			if (tags.get(i).trim().equalsIgnoreCase(newTag.text().trim())) {
				newTagButton.setEnabled(false);
				return;
			}
		}
	}
	
	@SuppressWarnings("unused")
	private void addTag() {
		String tag = newTag.text().trim();
		tagList.addItem(tag);
		newTag.setText("");
		newTagButton.setEnabled(false);
		
		for (int i=0; i<tagList.count(); i++) {
			QListWidgetItem item = tagList.item(i);
			if (item.text().equals(tag)) {
				item.setSelected(true);
				return;
			}
		}
	}
	
	
	public QListWidget getTagList() {
		return tagList;
	}
}
