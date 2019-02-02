package org.elastic.jdt.ls.core.internal.ant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.eclipse.ant.core.AntSecurityException;
import org.eclipse.ant.internal.core.IAntCoreConstants;
import org.eclipse.jface.text.IRegion;
import org.xml.sax.Attributes;

public class AntPropertyNode extends AntTaskNode {

	private String fValue = null;
	private String fReferencedName;
	private String fOccurrencesStartingPoint = IAntCoreConstants.VALUE;
	private String fOccurrencesIdentifier;

	/*
	 * The set of properties defined by this node name-> value mapping
	 */
	private Map<String, String> fProperties = null;

	public AntPropertyNode(Task task, Attributes attributes) {
		super(task);
		String label = attributes.getValue(IAntCoreConstants.NAME);
		if (label == null) {
			label = attributes.getValue(IAntCoreConstants.FILE);
			if (label != null) {
				fReferencedName = label;
				label = "file=" + label; //$NON-NLS-1$
			} else {
				label = attributes.getValue(IAntModelConstants.ATTR_RESOURCE);
				if (label != null) {
					fReferencedName = label;
					label = "resource=" + label; //$NON-NLS-1$
				} else {
					label = attributes.getValue(IAntModelConstants.ATTR_ENVIRONMENT);
					if (label != null) {
						label = "environment=" + label; //$NON-NLS-1$
					} else {
						label = attributes.getValue("srcFile"); //$NON-NLS-1$
						if (label != null) {
							fReferencedName = label;
							label = "srcFile=" + label; //$NON-NLS-1$
						}
					}
				}
			}
		} else {
			fValue = attributes.getValue(IAntCoreConstants.VALUE);
			if (fValue == null) {
				fOccurrencesStartingPoint = IAntModelConstants.ATTR_LOCATION;
				fValue = attributes.getValue(fOccurrencesStartingPoint);
			}
		}
		setBaseLabel(label);
	}

	public String getProperty(String propertyName) {
		if (fProperties != null) {
			return fProperties.get(propertyName);
		}
		return null;
	}

	/**
	 * Sets the properties in the project.
	 */
	@Override
	public boolean configure(boolean validateFully) {
		if (fConfigured) {
			return false;
		}
		try {
			getProjectNode().setCurrentConfiguringProperty(this);
			getTask().maybeConfigure();
			getTask().execute();
			fConfigured = true;
		}
		catch (BuildException be) {
			//
		}
		catch (LinkageError le) {
			// A classpath problem with the property task. Known cause:
			// <property name= "hey" refId= "classFileSetId"/> where
			// classFileSetId refs a ClassFileSet which is an optional type that requires
			// BCEL JAR. Currently it is not possible to set these types of properties within the Ant Editor.
			// see bug 71888
			//
		}
		catch (AntSecurityException se) {
			// either a system exit or setting of system property was attempted
			//
		}
		finally {
			getProjectNode().setCurrentConfiguringProperty(null);
		}
		return false;
	}

	/**
	 * Adds this property name and value as being created by this property node declaration.
	 * 
	 * @param propertyName
	 *            the name of the property
	 * @param value
	 *            the value of the property
	 */
	public void addProperty(String propertyName, String value) {
		if (fProperties == null) {
			fProperties = new HashMap<>(1);
		}
		fProperties.put(propertyName, value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ant.internal.ui.model.AntElementNode#containsOccurrence(java.lang.String)
	 */
	@Override
	public boolean containsOccurrence(String identifier) {
		if (!getTask().getTaskName().equals("property")) { //$NON-NLS-1$
			return super.containsOccurrence(identifier);
		}

		if (fValue != null) {
			return fValue.indexOf(identifier) != -1;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ant.internal.ui.model.AntElementNode#getOccurrencesIdentifier()
	 */
	@Override
	public String getOccurrencesIdentifier() {
		if (fOccurrencesIdentifier == null) {
			fOccurrencesIdentifier = new StringBuffer("${").append(fBaseLabel).append('}').toString(); //$NON-NLS-1$
		}
		return fOccurrencesIdentifier;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ant.internal.ui.model.AntElementNode#isRegionPotentialReference(org.eclipse.jface.text.IRegion)
	 */
	@Override
	public boolean isRegionPotentialReference(IRegion region) {
		boolean superOK = super.isRegionPotentialReference(region);
		if (!getTask().getTaskName().equals("property") || !superOK) { //$NON-NLS-1$
			return superOK;
		}

		String textToSearch = getAntModel().getText(getOffset(), getLength());
		if (textToSearch == null) {
			return false;
		}
		int valueOffset = textToSearch.indexOf(fOccurrencesStartingPoint);
		if (valueOffset > -1) {
			valueOffset = textToSearch.indexOf('"', valueOffset);
			if (valueOffset > -1) {
				boolean inValue = region.getOffset() >= (getOffset() + valueOffset);
				if (inValue) {
					if ("{".equals(getAntModel().getText(region.getOffset() - 1, 1)) || "}".equals(getAntModel().getText(region.getOffset() + region.getLength(), 1))) { //$NON-NLS-1$ //$NON-NLS-2$
						return true;
					}
					// reference is not in the value and not within a property de-reference
					return false;
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public List<Integer> computeIdentifierOffsets(String identifier) {
		if (!getTask().getTaskName().equals("property")) { //$NON-NLS-1$
			return super.computeIdentifierOffsets(identifier);
		}
		String textToSearch = getAntModel().getText(getOffset(), getLength());
		if (textToSearch == null || textToSearch.length() == 0 || identifier.length() == 0) {
			return null;
		}
		List<Integer> results = new ArrayList<>();
		if (fBaseLabel != null) {
			if (fBaseLabel.equals(identifier)) {
				int nameOffset = textToSearch.indexOf(IAntCoreConstants.NAME);
				nameOffset = textToSearch.indexOf(identifier, nameOffset + 1);
				results.add(Integer.valueOf(getOffset() + nameOffset));
			}
		}
		if (fValue != null) {
			int valueOffset = textToSearch.indexOf(fOccurrencesStartingPoint);
			int endOffset = getOffset() + getLength();
			while (valueOffset < endOffset) {
				valueOffset = textToSearch.indexOf(identifier, valueOffset);
				if (valueOffset == -1 || valueOffset > endOffset) {
					break;
				}
				results.add(Integer.valueOf(getOffset() + valueOffset));
				valueOffset += identifier.length();
			}
		}
		return results;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ant.internal.ui.model.AntElementNode#isFromDeclaration(org.eclipse.jface.text.IRegion)
	 */
	@Override
	public boolean isFromDeclaration(IRegion region) {
		if (fBaseLabel == null) {
			return false;
		}
		if (fBaseLabel.length() != region.getLength()) {
			return false;
		}
		int offset = getOffset();
		String textToSearch = getAntModel().getText(getOffset(), getLength());
		if (textToSearch == null || textToSearch.length() == 0) {
			return false;
		}
		int nameStartOffset = textToSearch.indexOf(IAntCoreConstants.NAME);
		nameStartOffset = textToSearch.indexOf("\"", nameStartOffset); //$NON-NLS-1$
		int nameEndOffset = textToSearch.indexOf("\"", nameStartOffset + 1); //$NON-NLS-1$
		nameEndOffset += offset;
		nameStartOffset += offset;
		return nameStartOffset <= region.getOffset() && region.getOffset() + region.getLength() <= nameEndOffset;
	}
}