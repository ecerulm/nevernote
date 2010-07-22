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


package cx.fbn.nevernote.filters;

public class NotebookCounter {
	private String	guid;
	private int 	count;
	
	
	public NotebookCounter() {
		guid = new String("");
		count = 0;
	}
	public NotebookCounter(NotebookCounter n) {
		guid = new String(n.getGuid());
		count = n.getCount();
	}
	public void setGuid(String g) {
		guid = g;
	}
	public String getGuid() {
		return guid;
	}
	public void setCount(int i) {
		count = i;
	}
	public int getCount() {
		return count;
	}
	
}
