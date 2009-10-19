/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.graphics;

import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.motif.*;
import org.eclipse.swt.*;

/**
 * <code>TextLayout</code> is a graphic object that represents
 * styled text.
 * <p>
 * Instances of this class provide support for drawing, cursor
 * navigation, hit testing, text wrapping, alignment, tab expansion
 * line breaking, etc.  These are aspects required for rendering internationalized text.
 * </p><p>
 * Application code must explicitly invoke the <code>TextLayout#dispose()</code> 
 * method to release the operating system resources managed by each instance
 * when those instances are no longer required.
 * </p>
 * 
 * @see <a href="http://www.eclipse.org/swt/snippets/#textlayout">TextLayout, TextStyle snippets</a>
 * @see <a href="http://www.eclipse.org/swt/examples.php">SWT Example: CustomControlExample, StyledText tab</a>
 * @see <a href="http://www.eclipse.org/swt/">Sample code and further information</a>
 * 
 * @since 3.0
 */
public final class TextLayout extends Resource {
	Font font;
	String text;
	int lineSpacing;
	int ascent, descent;
	int alignment;
	int wrapWidth;
	int orientation;
	int indent;
	boolean justify; 
	int[] tabs;
	int[] segments;
	StyleItem[] styles;
	
	StyleItem[][] runs;
	int[] lineOffset, lineY, lineWidth;
	int defaultAscent, defaultDescent;
	
	static final RGB LINK_FOREGROUND = new RGB (0, 51, 153);
	
	static class StyleItem {
		TextStyle style;
		int start, length, width, height, baseline;
		boolean lineBreak, softBreak, tab;

		public String toString () {
			return "StyleItem {" + start + ", " + style + "}";
		}
	}
	
/**	 
 * Constructs a new instance of this class on the given device.
 * <p>
 * You must dispose the text layout when it is no longer required. 
 * </p>
 * 
 * @param device the device on which to allocate the text layout
 * 
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if device is null and there is no current device</li>
 * </ul>
 * 
 * @see #dispose()
 */
public TextLayout (Device device) {
	super(device);
	wrapWidth = ascent = descent = -1;
	lineSpacing = 0;
	orientation = SWT.LEFT_TO_RIGHT;
	XFontStruct fontStruct = getFontHeigth(this.device.getSystemFont());
	defaultAscent = fontStruct.ascent;
	defaultDescent = fontStruct.descent;
	styles = new StyleItem[2];
	styles[0] = new StyleItem();
	styles[1] = new StyleItem();
	text = ""; //$NON-NLS-1$
	init();
}

void checkLayout () {
	if (isDisposed()) SWT.error(SWT.ERROR_GRAPHIC_DISPOSED);
}

int stringWidth (StyleItem run, char[] ch) {
	if (ch.length == 0) return 0;
	Font font = getItemFont(run);
	int fontList = font.handle;
	byte[] buffer = Converter.wcsToMbcs(font.codePage, ch, true);
	int xmString = OS.XmStringCreateLocalized(buffer);
	int width = OS.XmStringWidth(fontList, xmString);
	OS.XmStringFree(xmString);
	return width;
}

void computeRuns () {
	if (runs != null) return;
	StyleItem[] allRuns = itemize();
	for (int i=0; i<allRuns.length-1; i++) {
		StyleItem run = allRuns[i];
		place(run);
	}
	int lineWidth = 0, lineStart = 0, lineCount = 1;
	for (int i=0; i<allRuns.length - 1; i++) {
		StyleItem run = allRuns[i];
		if (run.length == 1) {
			char ch = text.charAt(run.start);
			switch (ch) {
				case '\t': {
					run.tab = true;
					run.baseline = 0;
					if (tabs == null) break;
					int tabsLength = tabs.length, j;
					for (j = 0; j < tabsLength; j++) {
						if (tabs[j] > lineWidth) {
							run.width = tabs[j] - lineWidth;
							break;
						}
					}
					if (j == tabsLength) {
						int tabX = tabs[tabsLength-1];
						int lastTabWidth = tabsLength > 1 ? tabs[tabsLength-1] - tabs[tabsLength-2] : tabs[0];
						if (lastTabWidth > 0) {
							while (tabX <= lineWidth) tabX += lastTabWidth;
							run.width = tabX - lineWidth;
						}
					}
					break;
				}
				case '\n':
					run.lineBreak = true;
					run.baseline = run.width = 0;
					break;
				case '\r':
					run.lineBreak = true;
					run.baseline = run.width = 0;
					StyleItem next = allRuns[i + 1];
					if (next.length != 0 && text.charAt(next.start) == '\n') {
						run.length += 1;
						i++;
					}
					break;
			}
		}
		if (wrapWidth != -1 && lineWidth + run.width > wrapWidth && !run.tab) {
			int start = 0;
			char[] chars = new char[run.length];
			text.getChars(run.start, run.start + run.length, chars, 0);
			if (!(run.style != null && run.style.metrics != null)) {
				int width = 0, maxWidth = wrapWidth - lineWidth;
				char[] buffer = new char[1];
				buffer[0] = chars[start];
				int charWidth = stringWidth(run, buffer);
				while (width + charWidth < maxWidth) {
					width += charWidth;
					start++;
					buffer[0] = chars[start];
					charWidth =	stringWidth(run, buffer);
				}
			}
			int firstStart = start;
			int firstIndice = i;			
			while (i >= lineStart) {
				chars = new char[run.length];
				text.getChars(run.start, run.start + run.length, chars, 0);
				while(start >= 0) {
					if (Compatibility.isSpaceChar(chars[start]) || Compatibility.isWhitespace(chars[start])) break;
					start--;
				}
				if (start >= 0 || i == lineStart) break;
				run = allRuns[--i];
				start = run.length - 1;
			}
			if (start == 0 && i != lineStart) {
				run = allRuns[--i];
			} else if (start <= 0 && i == lineStart) {
				i = firstIndice; 
				run = allRuns[i];
				start = Math.max(1, firstStart);
			}
			chars = new char[run.length];
			text.getChars(run.start, run.start + run.length, chars, 0);
			while (start < run.length) {
				if (!Compatibility.isWhitespace(chars[start])) break;
				start++;
			}
			if (0 < start && start < run.length) {
				StyleItem newRun = new StyleItem();
				newRun.start = run.start + start;
				newRun.length = run.length - start;
				newRun.style = run.style;
				run.length = start;
				place (run);
				place (newRun);
				StyleItem[] newAllRuns = new StyleItem[allRuns.length + 1];
				System.arraycopy(allRuns, 0, newAllRuns, 0, i + 1);
				System.arraycopy(allRuns, i + 1, newAllRuns, i + 2, allRuns.length - i - 1);
				allRuns = newAllRuns;
				allRuns[i + 1] = newRun;
			}
			if (i != allRuns.length - 2) {
				run.softBreak = run.lineBreak = true;
			}
		}
		lineWidth += run.width;
		if (run.lineBreak) {
			lineStart = i + 1;
			lineWidth = 0;
			lineCount++;
		}
	}
	lineWidth = 0;
	runs = new StyleItem[lineCount][];
	lineOffset = new int[lineCount + 1];
	lineY = new int[lineCount + 1];
	this.lineWidth = new int[lineCount];
	int lineRunCount = 0, line = 0;
	int ascent = Math.max(defaultAscent, this.ascent);
	int descent = Math.max(defaultDescent, this.descent);
	StyleItem[] lineRuns = new StyleItem[allRuns.length];
	XFontStruct fontStruct;
	for (int i=0; i<allRuns.length; i++) {
		StyleItem run = allRuns[i];
		lineRuns[lineRunCount++] = run;
		lineWidth += run.width;
		if (run.style != null ) {
			int runAscent = defaultAscent;
			int runDescent = defaultDescent;
			if (run.style.metrics != null) {
				GlyphMetrics metrics = run.style.metrics;
				runAscent = metrics.ascent;
				runDescent = metrics.descent;
			} else if (run.style.font != null) {
				fontStruct = getFontHeigth(run.style.font);
				runAscent = fontStruct.ascent;
				runDescent = fontStruct.descent;
			}
			ascent = Math.max(ascent, runAscent + run.style.rise);
			descent = Math.max(descent, runDescent - run.style.rise);
			if (run.style.rise != 0) {
				run.baseline += run.style.rise;
			}
		}
		if (run.lineBreak || i == allRuns.length - 1) {
			runs[line] = new StyleItem[lineRunCount];
			System.arraycopy(lineRuns, 0, runs[line], 0, lineRunCount);
			StyleItem lastRun = runs[line][lineRunCount - 1];
			this.lineWidth[line] = lineWidth;
			line++;
			lineY[line] = lineY[line - 1] + ascent + descent + lineSpacing;
			lineOffset[line] = lastRun.start + lastRun.length;
			lineRunCount = lineWidth = 0;
			ascent = Math.max(defaultAscent, this.ascent);
			descent = Math.max(defaultDescent, this.descent);
		}
	}
}

void destroy() {
	freeRuns();
	font = null;
	text = null;
	tabs = null;
	styles = null;
	lineOffset = null;
	lineY = null;
	lineWidth = null;
}

/**
 * Draws the receiver's text using the specified GC at the specified
 * point.
 * 
 * @param gc the GC to draw
 * @param x the x coordinate of the top left corner of the rectangular area where the text is to be drawn
 * @param y the y coordinate of the top left corner of the rectangular area where the text is to be drawn
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the gc is null</li>
 * </ul>
 */
public void draw (GC gc, int x, int y) {
	draw(gc, x, y, -1, -1, null, null);
}

/**
 * Draws the receiver's text using the specified GC at the specified
 * point.
 * 
 * @param gc the GC to draw
 * @param x the x coordinate of the top left corner of the rectangular area where the text is to be drawn
 * @param y the y coordinate of the top left corner of the rectangular area where the text is to be drawn
 * @param selectionStart the offset where the selections starts, or -1 indicating no selection
 * @param selectionEnd the offset where the selections ends, or -1 indicating no selection
 * @param selectionForeground selection foreground, or NULL to use the system default color
 * @param selectionBackground selection background, or NULL to use the system default color
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the gc is null</li>
 * </ul>
 */
public void draw(GC gc, int x, int y, int selectionStart, int selectionEnd, Color selectionForeground, Color selectionBackground) {
	draw(gc, x, y, selectionStart, selectionEnd, selectionForeground, selectionBackground, 0);
}

/**
 * Draws the receiver's text using the specified GC at the specified
 * point.
 * <p>
 * The parameter <code>flags</code> can include one of <code>SWT.DELIMITER_SELECTION</code>
 * or <code>SWT.FULL_SELECTION</code> to specify the selection behavior on all lines except
 * for the last line, and can also include <code>SWT.LAST_LINE_SELECTION</code> to extend
 * the specified selection behavior to the last line.
 * </p>
 * @param gc the GC to draw
 * @param x the x coordinate of the top left corner of the rectangular area where the text is to be drawn
 * @param y the y coordinate of the top left corner of the rectangular area where the text is to be drawn
 * @param selectionStart the offset where the selections starts, or -1 indicating no selection
 * @param selectionEnd the offset where the selections ends, or -1 indicating no selection
 * @param selectionForeground selection foreground, or NULL to use the system default color
 * @param selectionBackground selection background, or NULL to use the system default color
 * @param flags drawing options
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the gc is null</li>
 * </ul>
 * 
 * @since 3.3
 */
public void draw(GC gc, int x, int y, int selectionStart, int selectionEnd, Color selectionForeground, Color selectionBackground, int flags) {
	checkLayout();
	computeRuns();
	if (gc == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	if (gc.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	if (selectionForeground != null && selectionForeground.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	if (selectionBackground != null && selectionBackground.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	gc.checkGC(GC.FOREGROUND);
	int length = text.length(); 
	if (length == 0 && flags == 0) return;
	boolean hasSelection = selectionStart <= selectionEnd && selectionStart != -1 && selectionEnd != -1;
	if (hasSelection || (flags & SWT.LAST_LINE_SELECTION) != 0) {
		selectionStart = Math.min(Math.max(0, selectionStart), length - 1);
		selectionEnd = Math.min(Math.max(0, selectionEnd), length - 1);		
		if (selectionForeground == null) selectionForeground = device.getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
		if (selectionBackground == null) selectionBackground = device.getSystemColor(SWT.COLOR_LIST_SELECTION);
	}
	final Color foreground = gc.getForeground();
	final Color background = gc.getBackground();
	final Font gcFont = gc.getFont();
	Color linkColor = null;
	Rectangle clip = gc.getClipping();
	for (int line=0; line<runs.length; line++) {
		int drawX = x + getLineIndent(line);
		int drawY = y + lineY[line];
		StyleItem[] lineRuns = runs[line];
		int lineHeight = lineY[line+1] - lineY[line];
		if (flags != 0 && (hasSelection || (flags & SWT.LAST_LINE_SELECTION) != 0)) {
			boolean extent = false;
			if (line == runs.length - 1 && (flags & SWT.LAST_LINE_SELECTION) != 0) {
				extent = true;
			} else {
				StyleItem run = lineRuns[lineRuns.length - 1];
				if (run.lineBreak && !run.softBreak) {
					if (selectionStart <= run.start && run.start <= selectionEnd) extent = true;
				} else {
					int endOffset = run.start + run.length - 1;
					if (selectionStart <= endOffset && endOffset < selectionEnd && (flags & SWT.FULL_SELECTION) != 0) {
						extent = true;
					}
				}
			}
			if (extent) {
				gc.setBackground(selectionBackground);
				int width = (flags & SWT.FULL_SELECTION) != 0 ? 0x7fffffff : lineHeight / 3;
				gc.fillRectangle(drawX + lineWidth[line], drawY, width, lineHeight);
			}
		}
		if (drawX > clip.x + clip.width) continue;
		if (drawX + lineWidth[line] < clip.x) continue;
		int baseline = Math.max(0, this.ascent);
		for (int i = 0; i < lineRuns.length; i++) {
			baseline = Math.max(baseline, lineRuns[i].baseline);
		}
		for (int i = 0; i < lineRuns.length; i++) {
			StyleItem run = lineRuns[i];
			if (run.length == 0) continue;
			if (drawX > clip.x + clip.width) break;
			if (drawX + run.width >= clip.x) {
				if (!run.lineBreak || run.softBreak) {
					String string = text.substring(run.start, run.start + run.length);
					int drawRunY = drawY + (baseline - run.baseline);
					int end = run.start + run.length - 1;
					gc.setFont(getItemFont(run));
					boolean fullSelection = hasSelection && selectionStart <= run.start && selectionEnd >= end;
					TextStyle style = run.style;
					if (fullSelection) {
						gc.setBackground(selectionBackground);
						gc.fillRectangle(drawX, drawY, run.width, lineHeight);
						if (!run.tab && !(style != null && style.metrics != null)) {
							gc.setForeground(selectionForeground);
							gc.drawString(string, drawX, drawRunY, true);
							drawLines(gc, run, drawX, drawRunY, run.width);
						}
					} else {
						if (style != null && style.background != null) {
							Color bg = style.background;
							gc.setBackground(bg);
							gc.fillRectangle(drawX, drawRunY, run.width, run.height);
						}
						if (!run.tab) {
							Color fg = foreground;
							if (style != null) {
								if (style.foreground != null) {
									fg = style.foreground;
								} else {
									if (style.underline && style.underlineStyle == SWT.UNDERLINE_LINK) {
										if (linkColor == null) {
											linkColor = new Color(device, LINK_FOREGROUND);
										}
										fg = linkColor;
									}
								}
							}
							if (!(style != null && style.metrics != null)) {
								gc.setForeground(fg);
								gc.drawString(string, drawX, drawRunY, true);
								drawLines(gc, run, drawX, drawRunY, run.width);
							}
							boolean partialSelection = hasSelection && !(selectionStart > end || run.start > selectionEnd);
							if (partialSelection) {
								int selStart = Math.max(selectionStart, run.start);
								int selEnd = Math.min(selectionEnd, end);
								string = text.substring(run.start, selStart);
								int selX = drawX + gc.stringExtent(string).x;
								string = text.substring(selStart, selEnd + 1);
								int selWidth = gc.stringExtent(string).x;
								gc.setBackground(selectionBackground);
								gc.fillRectangle(selX, drawY, selWidth, lineHeight);
								if (fg != selectionForeground && !(style != null && style.metrics != null)) {
									gc.setForeground(selectionForeground);
									gc.drawString(string, selX, drawRunY, true);
									drawLines(gc, run, selX, drawRunY, selWidth);
								}
							}
						}
					}
					drawBorder(gc, lineRuns, i, drawX, drawY, lineHeight, foreground);
				}
			}
			drawX += run.width;
		}
	}
	gc.setForeground(foreground);
	gc.setBackground(background);
	gc.setFont(gcFont);
	if (linkColor != null) linkColor.dispose();
}

void drawBorder(GC gc, StyleItem[] line, int index, int x, int y, int lineHeight, Color color) {
	StyleItem run = line[index];
	TextStyle style = run.style;
	if (style == null) return;
	if (style.borderStyle != SWT.NONE && (index + 1 >= line.length || !style.isAdherentBorder(line[index + 1].style))) {
		int width = run.width;
		for (int i = index; i > 0 && style.isAdherentBorder(line[i - 1].style); i--) {
			x -= line[i - 1].width;
			width += line[i - 1].width;
		}
		if (style.borderColor != null) {
			color = style.borderColor; 
		} else {
			if (style.foreground != null) {
				color = style.foreground;
			}
		}
		gc.setForeground(color);
		int lineStyle = gc.getLineStyle();
		int[] dashes = null;
		if (lineStyle == SWT.LINE_CUSTOM) {
			dashes = gc.getLineDash();
		}
		switch (style.borderStyle) {
			case SWT.BORDER_SOLID:
				gc.setLineStyle(SWT.LINE_SOLID);
				break;
			case SWT.BORDER_DASH:
				gc.setLineStyle(SWT.LINE_DASH);
				break;
			case SWT.BORDER_DOT:
				gc.setLineStyle(SWT.LINE_DOT);
				break;
		}
		gc.drawRectangle(x, y, width - 1, lineHeight - 1);
		gc.setLineStyle(lineStyle);
		if (dashes != null) {
			gc.setLineDash(dashes);
		}
	}
} 

void drawLines(GC gc, StyleItem run, int x, int y, int width) {
	TextStyle style = run.style;
	if (style == null) return;
	if (style.underline) {
		int underlineY = y + run.baseline + 1 - style.rise;
		if (style.underlineColor != null) {
			gc.setForeground(style.underlineColor);
		}
		switch (style.underlineStyle) {
			case SWT.UNDERLINE_SQUIGGLE:
			case SWT.UNDERLINE_ERROR:
				int squigglyThickness = 1;
				int squigglyHeight = 2 * squigglyThickness;
				int squigglyY = Math.min(underlineY, y + run.height - squigglyHeight - 1);
				int[] points = computePolyline(x, squigglyY, x + width, squigglyY + squigglyHeight);
				gc.drawPolyline(points);
				break;
			case SWT.UNDERLINE_DOUBLE:
				gc.drawLine (x, underlineY + 2, x + width, underlineY + 2);
				//FALLTHROU
			case SWT.UNDERLINE_LINK:
			case SWT.UNDERLINE_SINGLE:	
				gc.drawLine (x, underlineY, x + width, underlineY);
		}
	}
	if (style.strikeout) {
		int strikeoutY = y + run.height - run.height/2 - 1;
		if (style.strikeoutColor != null) {
			gc.setForeground(style.strikeoutColor);
		}
		gc.drawLine (x, strikeoutY, x + width, strikeoutY);
	}
}

int[] computePolyline(int left, int top, int right, int bottom) {
	int height = bottom - top; // can be any number
	int width = 2 * height; // must be even
	int peaks = (right - left) / width;
	if (peaks == 0 && right - left > 2) {
		peaks = 1;
	}
	int length = ((2 * peaks) + 1) * 2;
	if (length < 0) return new int[0];
	
	int[] coordinates = new int[length];
	for (int i = 0; i < peaks; i++) {
		int index = 4 * i;
		coordinates[index] = left + (width * i);
		coordinates[index+1] = bottom;
		coordinates[index+2] = coordinates[index] + width / 2;
		coordinates[index+3] = top;
	}
	coordinates[length-2] = Math.min(Math.max(0, right - 1), left + (width * peaks));
	coordinates[length-1] = bottom;
	return coordinates;
}

void freeRuns() {
	runs = null;
}

/** 
 * Returns the receiver's horizontal text alignment, which will be one
 * of <code>SWT.LEFT</code>, <code>SWT.CENTER</code> or
 * <code>SWT.RIGHT</code>.
 *
 * @return the alignment used to positioned text horizontally
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public int getAlignment () {
	checkLayout();
	return alignment;
}

/**
 * Returns the ascent of the receiver.
 *
 * @return the ascent
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #getDescent()
 * @see #setDescent(int)
 * @see #setAscent(int)
 * @see #getLineMetrics(int)
 */
public int getAscent () {
	checkLayout();
	return ascent;
}

/**
 * Returns the bounds of the receiver. The width returned is either the
 * width of the longest line or the width set using {@link TextLayout#setWidth(int)}.
 * To obtain the text bounds of a line use {@link TextLayout#getLineBounds(int)}.
 * 
 * @return the bounds of the receiver
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #setWidth(int)
 * @see #getLineBounds(int)
 */
public Rectangle getBounds () {
	checkLayout();
	computeRuns();
	int width = 0;
	if (wrapWidth != -1) {
		width = wrapWidth;
	} else {
		for (int line=0; line<runs.length; line++) {
			width = Math.max(width, lineWidth[line] + getLineIndent(line));
		}
	}
	return new Rectangle (0, 0, width, lineY[lineY.length - 1]);
}

/**
 * Returns the bounds for the specified range of characters. The
 * bounds is the smallest rectangle that encompasses all characters
 * in the range. The start and end offsets are inclusive and will be
 * clamped if out of range.
 * 
 * @param start the start offset
 * @param end the end offset
 * @return the bounds of the character range
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public Rectangle getBounds (int start, int end) {
	checkLayout();
	computeRuns();
	int length = text.length();
	if (length == 0) return new Rectangle(0, 0, 0, 0);
	if (start > end) return new Rectangle(0, 0, 0, 0);
	start = Math.min(Math.max(0, start), length - 1);
	end = Math.min(Math.max(0, end), length - 1);
	int startLine = getLineIndex(start);
	int endLine = getLineIndex(end);

	Rectangle rect = new Rectangle(0, 0, 0, 0);
	rect.y = lineY[startLine];
	rect.height = lineY[endLine + 1] - rect.y - lineSpacing;
	if (startLine == endLine) {
		rect.x = getLocation(start, false).x;
		rect.width = getLocation(end, true).x - rect.x;
	} else {
		while (startLine <= endLine) {
			rect.width = Math.max(rect.width, lineWidth[startLine++]);
		}
	}
	return rect;
}

/**
 * Returns the descent of the receiver.
 *
 * @return the descent
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #getAscent()
 * @see #setAscent(int)
 * @see #setDescent(int)
 * @see #getLineMetrics(int)
 */
public int getDescent () {
	checkLayout();
	return descent;
}

/** 
 * Returns the default font currently being used by the receiver
 * to draw and measure text.
 *
 * @return the receiver's font
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public Font getFont () {
	checkLayout();
	return font;
}

XFontStruct getFontHeigth(Font font) {
	int fontList = font.handle;
	int [] buffer = new int [1];
	if (!OS.XmFontListInitFontContext (buffer, fontList)) {
		SWT.error(SWT.ERROR_NO_HANDLES);
	}
	int context = buffer [0];
	int ascent = 0, descent = 0;
	XFontStruct fontStruct = new XFontStruct ();
	int fontListEntry;
	int [] fontStructPtr = new int [1];
	int [] fontNamePtr = new int [1];
	while ((fontListEntry = OS.XmFontListNextEntry (context)) != 0) {
		int fontPtr = OS.XmFontListEntryGetFont (fontListEntry, buffer);
		if (buffer [0] == 0) {
			OS.memmove (fontStruct, fontPtr, XFontStruct.sizeof);
			ascent = Math.max(ascent, fontStruct.ascent);
			descent = Math.max(descent, fontStruct.descent);			
		} else {
			int nFonts = OS.XFontsOfFontSet (fontPtr, fontStructPtr, fontNamePtr);
			int [] fontStructs = new int [nFonts];
			OS.memmove (fontStructs, fontStructPtr [0], nFonts * 4);
			for (int i=0; i<nFonts; i++) {
				OS.memmove (fontStruct, fontStructs[i], XFontStruct.sizeof);
				ascent = Math.max(ascent, fontStruct.ascent);
				descent = Math.max(descent, fontStruct.descent);			
			}
		}
	}
	OS.XmFontListFreeFontContext (context);
	fontStruct.ascent = ascent;
	fontStruct.descent = descent;
	return fontStruct;
}

/**
* Returns the receiver's indent.
*
* @return the receiver's indent
* 
* @exception SWTException <ul>
*    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
* </ul>
* 
* @since 3.2
*/
public int getIndent () {
	checkLayout();
	return indent;
}

/**
* Returns the receiver's justification.
*
* @return the receiver's justification
* 
* @exception SWTException <ul>
*    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
* </ul>
* 
* @since 3.2
*/
public boolean getJustify () {
	checkLayout();
	return justify;
}

/**
 * Returns the embedding level for the specified character offset. The
 * embedding level is usually used to determine the directionality of a
 * character in bidirectional text.
 * 
 * @param offset the character offset
 * @return the embedding level
 * 
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the character offset is out of range</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 */
public int getLevel (int offset) {
	checkLayout();
	int length = text.length();
	if (!(0 <= offset && offset <= length)) SWT.error(SWT.ERROR_INVALID_RANGE);
	return 0;
}

/**
 * Returns the line offsets.  Each value in the array is the
 * offset for the first character in a line except for the last
 * value, which contains the length of the text.
 * 
 * @return the line offsets
 *  
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public int[] getLineOffsets () {
	checkLayout();
	computeRuns();
	int[] offsets = new int[lineOffset.length];
	System.arraycopy(lineOffset, 0, offsets, 0, offsets.length);
	return offsets;
}

/**
 * Returns the bounds of the line for the specified line index.
 * 
 * @param lineIndex the line index
 * @return the line bounds 
 * 
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the line index is out of range</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public Rectangle getLineBounds(int lineIndex) {
	checkLayout();
	computeRuns();
	if (!(0 <= lineIndex && lineIndex < runs.length)) SWT.error(SWT.ERROR_INVALID_RANGE);
	int x = getLineIndent(lineIndex);
	int y = lineY[lineIndex];
	int width = lineWidth[lineIndex];
	int height = lineY[lineIndex + 1] - y - lineSpacing;
	return new Rectangle (x, y, width, height);
}

/**
 * Returns the receiver's line count. This includes lines caused
 * by wrapping.
 *
 * @return the line count
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public int getLineCount () {
	checkLayout();
	computeRuns();
	return runs.length;
}

int getLineIndent (int lineIndex) {
	int lineIndent = 0;
	if (lineIndex == 0) {
		lineIndent = indent;
	} else {
		StyleItem[] previousLine = runs[lineIndex - 1];
		StyleItem previousRun = previousLine[previousLine.length - 1];
		if (previousRun.lineBreak && !previousRun.softBreak) {
			lineIndent = indent;
		}
	}
	if (wrapWidth != -1) {
		boolean partialLine = true;
//		if (justify) {
//			StyleItem[] lineRun = runs[lineIndex];
//			if (lineRun[lineRun.length - 1].softBreak) {
//				partialLine = false;
//			}
//		}
		if (partialLine) {
			int lineWidth = this.lineWidth[lineIndex] + lineIndent;
			switch (alignment) {
				case SWT.CENTER: lineIndent += (wrapWidth - lineWidth) / 2; break;
				case SWT.RIGHT: lineIndent += wrapWidth - lineWidth; break;
			}
		}
	}
	return lineIndent;
}

/**
 * Returns the index of the line that contains the specified
 * character offset.
 * 
 * @param offset the character offset
 * @return the line index
 * 
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the character offset is out of range</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public int getLineIndex (int offset) {
	checkLayout();
	computeRuns();
	int length = text.length();
	if (!(0 <= offset && offset <= length)) SWT.error(SWT.ERROR_INVALID_RANGE);
	for (int line=0; line<runs.length; line++) {
		if (lineOffset[line + 1] > offset) {
			return line;
		}
	}
	return runs.length - 1;
}

/**
 * Returns the font metrics for the specified line index.
 * 
 * @param lineIndex the line index
 * @return the font metrics 
 * 
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the line index is out of range</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public FontMetrics getLineMetrics (int lineIndex) {
	checkLayout();
	computeRuns();
	if (!(0 <= lineIndex && lineIndex < runs.length)) SWT.error(SWT.ERROR_INVALID_RANGE);
	int ascent = Math.max(defaultAscent, this.ascent);
	int descent = Math.max(defaultDescent, this.descent);
	if (text.length() != 0) {
		GC gc = new GC(device);
		StyleItem[] lineRuns = runs[lineIndex];
		for (int i = 0; i < lineRuns.length; i++) {
			StyleItem run = lineRuns[i];
			if (run.style != null) {
				int runAscent = 0;
				int runDescent = 0;
				if (run.style.metrics != null) {
					GlyphMetrics glyphMetrics = run.style.metrics;
					runAscent = glyphMetrics.ascent;
					runDescent = glyphMetrics.descent;
				} else if (run.style.font != null) {
					gc.setFont(run.style.font);
					FontMetrics metrics = gc.getFontMetrics();
					runAscent = metrics.getAscent();
					runDescent = metrics.getDescent();
				}
				ascent = Math.max(ascent, runAscent + run.style.rise);
				descent = Math.max(descent, runDescent - run.style.rise);
			}
		}
		gc.dispose();
	}
	return FontMetrics.motif_new(ascent, descent, 0, 0, ascent + descent);
}

/**
 * Returns the location for the specified character offset. The
 * <code>trailing</code> argument indicates whether the offset
 * corresponds to the leading or trailing edge of the cluster.
 * 
 * @param offset the character offset
 * @param trailing the trailing flag
 * @return the location of the character offset
 *  
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #getOffset(Point, int[])
 * @see #getOffset(int, int, int[])
 */
public Point getLocation (int offset, boolean trailing) {
	checkLayout();
	computeRuns();
	int length = text.length();
	if (!(0 <= offset && offset <= length)) SWT.error(SWT.ERROR_INVALID_RANGE);
	int line;
	for (line=0; line<runs.length; line++) {
		if (lineOffset[line + 1] > offset) break;
	}
	line = Math.min(line, runs.length - 1);
	StyleItem[] lineRuns = runs[line];
	Point result = null;
	if (offset == length) {
		result = new Point(lineWidth[line], lineY[line]);
	} else {
		int width = 0;
		for (int i=0; i<lineRuns.length; i++) {
			StyleItem run = lineRuns[i];
			int end = run.start + run.length;
			if (run.start <= offset && offset < end) {
				if (run.tab) {
					if (trailing || offset == length) width += run.width;
				} else {
					if (trailing) offset++;
					if (run.style != null && run.style.metrics != null) {
						GlyphMetrics metrics = run.style.metrics;
						width += metrics.width * (offset - run.start);
					} else {
						char[] chars = new char[offset - run.start];
						text.getChars(run.start, offset, chars, 0);
						width += stringWidth(run, chars);
					}
				}
				result = new Point(width, lineY[line]);
				break;
			}
			width += run.width;
		}
	}
	if (result == null) result = new Point(0, 0);
	result.x += getLineIndent(line);
	return result;
}

Font getItemFont(StyleItem item) {
	if (item.style != null && item.style.font != null) {
		return item.style.font;
	}
	if (this.font != null) {
		return this.font;
	}
	return device.systemFont;
}

/**
 * Returns the next offset for the specified offset and movement
 * type.  The movement is one of <code>SWT.MOVEMENT_CHAR</code>, 
 * <code>SWT.MOVEMENT_CLUSTER</code>, <code>SWT.MOVEMENT_WORD</code>,
 * <code>SWT.MOVEMENT_WORD_END</code> or <code>SWT.MOVEMENT_WORD_START</code>.
 * 
 * @param offset the start offset
 * @param movement the movement type 
 * @return the next offset
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the offset is out of range</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #getPreviousOffset(int, int)
 */
public int getNextOffset (int offset, int movement) {
	checkLayout();
	computeRuns();
	int length = text.length();
	if (!(0 <= offset && offset <= length)) SWT.error(SWT.ERROR_INVALID_RANGE);
	if (offset == length) return length;
	if ((movement & (SWT.MOVEMENT_CHAR | SWT.MOVEMENT_CLUSTER)) != 0) return offset + 1;
	int lineEnd = 0;
	for (int i=1; i<lineOffset.length; i++) {
		if (lineOffset[i] > offset) {
			lineEnd = Math.max(lineOffset[i - 1], lineOffset[i] - 1);
			if (i == runs.length) lineEnd++;
			break;
		}
	}
	boolean previousSpaceChar = !Compatibility.isLetterOrDigit(text.charAt(offset));
	offset++;
	while (offset < lineEnd) {
		boolean spaceChar = !Compatibility.isLetterOrDigit(text.charAt(offset));
		if (movement == SWT.MOVEMENT_WORD || movement == SWT.MOVEMENT_WORD_END) {
			if (spaceChar && !previousSpaceChar) break;
		}
		if (movement == SWT.MOVEMENT_WORD_START) {
			if (!spaceChar && previousSpaceChar) break;
		}
		previousSpaceChar = spaceChar;
		offset++;
	}
	return offset;
}

/**
 * Returns the character offset for the specified point.  
 * For a typical character, the trailing argument will be filled in to 
 * indicate whether the point is closer to the leading edge (0) or
 * the trailing edge (1).  When the point is over a cluster composed 
 * of multiple characters, the trailing argument will be filled with the 
 * position of the character in the cluster that is closest to
 * the point.
 * 
 * @param point the point
 * @param trailing the trailing buffer
 * @return the character offset
 *  
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the trailing length is less than <code>1</code></li>
 *    <li>ERROR_NULL_ARGUMENT - if the point is null</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #getLocation(int, boolean)
 */
public int getOffset (Point point, int[] trailing) {
	checkLayout();
	if (point == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
	return getOffset (point.x, point.y, trailing);
}

/**
 * Returns the character offset for the specified point.  
 * For a typical character, the trailing argument will be filled in to 
 * indicate whether the point is closer to the leading edge (0) or
 * the trailing edge (1).  When the point is over a cluster composed 
 * of multiple characters, the trailing argument will be filled with the 
 * position of the character in the cluster that is closest to
 * the point.
 * 
 * @param x the x coordinate of the point
 * @param y the y coordinate of the point
 * @param trailing the trailing buffer
 * @return the character offset
 *  
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the trailing length is less than <code>1</code></li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #getLocation(int, boolean)
 */
public int getOffset (int x, int y, int[] trailing) {
	checkLayout();
	computeRuns();
	if (trailing != null && trailing.length < 1) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	int line;
	int lineCount = runs.length;
	for (line=0; line<lineCount; line++) {
		if (lineY[line + 1] > y) break;
	}
	line = Math.min(line, runs.length - 1);
	x -= getLineIndent(line);
	if (x >= lineWidth[line]) x = lineWidth[line] - 1;
	if (x < 0) x = 0;
	StyleItem[] lineRuns = runs[line];
	int width = 0;
	for (int i = 0; i < lineRuns.length; i++) {
		StyleItem run = lineRuns[i];
		if (run.lineBreak && !run.softBreak) return run.start;
		if (width + run.width > x) {
			if (run.style != null && run.style.metrics != null) {
				int xRun = x - width;
				GlyphMetrics metrics = run.style.metrics;
				if (metrics.width > 0) {
					if (trailing != null) {
						trailing[0] = (xRun % metrics.width < metrics.width / 2) ? 0 : 1;
					}
					return run.start + xRun / metrics.width;
				}
			}
			if (run.tab) {
				if (trailing != null) {
					trailing[0] = x < (width + run.width / 2) ? 0 : 1; 
				}
				return run.start;
			}
			int offset = 0;
			char[] buffer = new char[1];
			char[] chars = new char[run.length];
			text.getChars(run.start, run.start + run.length, chars, 0);
			for (offset = 0; offset < chars.length; offset++) {
				buffer[0] = chars[offset];
				int charWidth = stringWidth(run, buffer);
				if (width + charWidth > x) {
					if (trailing != null) {
						trailing[0] = x < (width + charWidth / 2) ? 0 : 1;
					}
					break;
				}
				width += charWidth;
			}
			return run.start + offset; 
		}
		width += run.width;
	}
	if (trailing != null) trailing[0] = 0;
	return lineOffset[line + 1];
}

/**
 * Returns the orientation of the receiver.
 *
 * @return the orientation style
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public int getOrientation () {
	checkLayout();
	return orientation;
}

/**
 * Returns the previous offset for the specified offset and movement
 * type.  The movement is one of <code>SWT.MOVEMENT_CHAR</code>, 
 * <code>SWT.MOVEMENT_CLUSTER</code> or <code>SWT.MOVEMENT_WORD</code>,
 * <code>SWT.MOVEMENT_WORD_END</code> or <code>SWT.MOVEMENT_WORD_START</code>.
 * 
 * @param offset the start offset
 * @param movement the movement type 
 * @return the previous offset
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the offset is out of range</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #getNextOffset(int, int)
 */
public int getPreviousOffset (int offset, int movement) {
	checkLayout();
	computeRuns();
	int length = text.length();
	if (!(0 <= offset && offset <= length)) SWT.error(SWT.ERROR_INVALID_RANGE);
	if (offset == 0) return 0;
	if ((movement & (SWT.MOVEMENT_CHAR | SWT.MOVEMENT_CLUSTER)) != 0) return offset - 1;
	int lineStart = 0;
	for (int i=0; i<lineOffset.length-1; i++) {
		int lineEnd = lineOffset[i+1];
		if (i == runs.length - 1) lineEnd++;
		if (lineEnd > offset) {
			lineStart = lineOffset[i];
			break;
		}
	}	
	offset--;
	boolean previousSpaceChar = !Compatibility.isLetterOrDigit(text.charAt(offset));
	while (lineStart < offset) {
		boolean spaceChar = !Compatibility.isLetterOrDigit(text.charAt(offset - 1));
		if (movement == SWT.MOVEMENT_WORD_END) {
			if (!spaceChar && previousSpaceChar) break;
		}
		if (movement == SWT.MOVEMENT_WORD || movement == SWT.MOVEMENT_WORD_START) {
			if (spaceChar && !previousSpaceChar) break;
		}
		offset--;
		previousSpaceChar = spaceChar;
	}
	return offset;
}

/**
 * Gets the ranges of text that are associated with a <code>TextStyle</code>.
 *
 * @return the ranges, an array of offsets representing the start and end of each
 * text style. 
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #getStyles()
 * 
 * @since 3.2
 */
public int[] getRanges () {
	checkLayout();
	int[] result = new int[styles.length * 2];
	int count = 0;
	for (int i=0; i<styles.length - 1; i++) {
		if (styles[i].style != null) {
			result[count++] = styles[i].start;
			result[count++] = styles[i + 1].start - 1;
		}
	}
	if (count != result.length) {
		int[] newResult = new int[count];
		System.arraycopy(result, 0, newResult, 0, count);
		result = newResult;
	}
	return result;
}

/**
 * Returns the text segments offsets of the receiver.
 *
 * @return the text segments offsets
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public int[] getSegments() {
	checkLayout();
	return segments;
}

/**
 * Returns the line spacing of the receiver.
 *
 * @return the line spacing
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public int getSpacing () {
	checkLayout();	
	return lineSpacing;
}

/**
 * Gets the style of the receiver at the specified character offset.
 *
 * @param offset the text offset
 * @return the style or <code>null</code> if not set
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the character offset is out of range</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public TextStyle getStyle (int offset) {
	checkLayout();
	int length = text.length();
	if (!(0 <= offset && offset < length)) SWT.error(SWT.ERROR_INVALID_RANGE);
	for (int i=1; i<styles.length; i++) {
		StyleItem item = styles[i];
		if (item.start > offset) {
			return styles[i - 1].style;
		}
	}
	return null;
}

/**
 * Gets all styles of the receiver.
 *
 * @return the styles
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #getRanges()
 * 
 * @since 3.2
 */
public TextStyle[] getStyles () {
	checkLayout();
	TextStyle[] result = new TextStyle[styles.length];
	int count = 0;
	for (int i=0; i<styles.length; i++) {
		if (styles[i].style != null) {
			result[count++] = styles[i].style;
		}
	}
	if (count != result.length) {
		TextStyle[] newResult = new TextStyle[count];
		System.arraycopy(result, 0, newResult, 0, count);
		result = newResult;
	}
	return result;
}

/**
 * Returns the tab list of the receiver.
 *
 * @return the tab list
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public int[] getTabs () {
	checkLayout();
	return tabs;
}

/**
 * Gets the receiver's text, which will be an empty
 * string if it has never been set.
 *
 * @return the receiver's text
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public String getText () {
	checkLayout();
	return text;
}

/**
 * Returns the width of the receiver.
 *
 * @return the width
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public int getWidth () {
	checkLayout();
	return wrapWidth;
}

/**
 * Returns <code>true</code> if the text layout has been disposed,
 * and <code>false</code> otherwise.
 * <p>
 * This method gets the dispose state for the text layout.
 * When a text layout has been disposed, it is an error to
 * invoke any other method using the text layout.
 * </p>
 *
 * @return <code>true</code> when the text layout is disposed and <code>false</code> otherwise
 */
public boolean isDisposed () {
	return device == null;
}

/*
 *  Itemize the receiver text, create run for 
 */
StyleItem[] itemize () {
	int length = text.length();
	if (length == 0) {
		return new StyleItem[]{new StyleItem(), new StyleItem()};
	}
	int runCount = 0, start = 0;
	StyleItem[] runs = new StyleItem[length];
	char[] chars = text.toCharArray();
	for (int i = 0; i<length; i++) {
		char ch = chars[i];
		if (ch == '\t' || ch == '\r' || ch == '\n') {
			if (i != start) {
				StyleItem item = new StyleItem();
				item.start = start;
				runs[runCount++] = item;
			}
			StyleItem item = new StyleItem();
			item.start = i;
			runs[runCount++] = item;
			start = i + 1;
		}
	}
	char lastChar = chars[length - 1];
	if (!(lastChar == '\t' || lastChar == '\r' || lastChar == '\n')) {
		StyleItem item = new StyleItem();
		item.start = start;
		runs[runCount++] = item;
	}
	if (runCount != length) {
		StyleItem[] newRuns = new StyleItem[runCount];
		System.arraycopy(runs, 0, newRuns, 0, runCount);
		runs = newRuns;
	}
	runs = merge(runs, runCount);
	return runs;
}

/* 
 *  Merge styles ranges and script items 
 */
StyleItem[] merge (StyleItem[] items, int itemCount) {
	int length = text.length();
	int count = 0, start = 0, end = length, itemIndex = 0, styleIndex = 0;
	StyleItem[] runs = new StyleItem[itemCount + styles.length];
	while (start < end) {
		StyleItem item = new StyleItem();
		item.start = start;
		item.style = styles[styleIndex].style;
		runs[count++] = item;
		int itemLimit = itemIndex + 1 < items.length ? items[itemIndex + 1].start : length;
		int styleLimit = styleIndex + 1 < styles.length ? styles[styleIndex + 1].start : length;
		if (styleLimit <= itemLimit) {
			styleIndex++;
			start = styleLimit;
		}
		if (itemLimit <= styleLimit) {
			itemIndex++;
			start = itemLimit;
		}
		item.length = start - item.start;
	}
	StyleItem item = new StyleItem();
	item.start = end;
	runs[count++] = item;
	if (runs.length != count) {
		StyleItem[] result = new StyleItem[count];
		System.arraycopy(runs, 0, result, 0, count);
		return result;
	}
	return runs;
}

void place (StyleItem run) {
	if (run.length == 0) return;
	if (run.style != null && run.style.metrics != null) {
		GlyphMetrics glyphMetrics = run.style.metrics;
		run.width = glyphMetrics.width * run.length;
		run.baseline = glyphMetrics.ascent;
		run.height = glyphMetrics.ascent + glyphMetrics.descent;
	} else {
		char[] chars = new char[run.length];
		text.getChars(run.start, run.start + run.length, chars, 0);
		Font font = getItemFont(run);
		int fontList = font.handle;
		byte[] buffer = Converter.wcsToMbcs(font.codePage, chars, true);
		short[] width = new short[1], height = new short[1];
		int xmString = OS.XmStringCreateLocalized(buffer);
		OS.XmStringExtent(fontList, xmString, width, height);
		run.width = width[0] & 0xFFFF;
		run.height = height[0] & 0xFFFF;
		run.baseline = OS.XmStringBaseline(fontList, xmString);
		OS.XmStringFree(xmString);
	}
}

/**
 * Sets the text alignment for the receiver. The alignment controls
 * how a line of text is positioned horizontally. The argument should
 * be one of <code>SWT.LEFT</code>, <code>SWT.RIGHT</code> or <code>SWT.CENTER</code>.
 * <p>
 * The default alignment is <code>SWT.LEFT</code>.  Note that the receiver's
 * width must be set in order to use <code>SWT.RIGHT</code> or <code>SWT.CENTER</code>
 * alignment.
 * </p>
 *
 * @param alignment the new alignment 
 *
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #setWidth(int)
 */
public void setAlignment (int alignment) {
	checkLayout();
	int mask = SWT.LEFT | SWT.CENTER | SWT.RIGHT;
	alignment &= mask;
	if (alignment == 0) return;
	if ((alignment & SWT.LEFT) != 0) alignment = SWT.LEFT;
	if ((alignment & SWT.RIGHT) != 0) alignment = SWT.RIGHT;
	freeRuns();
	this.alignment = alignment;
}

/**
 * Sets the ascent of the receiver. The ascent is distance in pixels
 * from the baseline to the top of the line and it is applied to all
 * lines. The default value is <code>-1</code> which means that the
 * ascent is calculated from the line fonts.
 *
 * @param ascent the new ascent
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the ascent is less than <code>-1</code></li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #setDescent(int)
 * @see #getLineMetrics(int)
 */
public void setAscent (int ascent) {
	checkLayout();
	if (ascent < -1) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	if (this.ascent == ascent) return;
	freeRuns();
	this.ascent = ascent;
}

/**
 * Sets the descent of the receiver. The descent is distance in pixels
 * from the baseline to the bottom of the line and it is applied to all
 * lines. The default value is <code>-1</code> which means that the
 * descent is calculated from the line fonts.
 *
 * @param descent the new descent
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the descent is less than <code>-1</code></li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #setAscent(int)
 * @see #getLineMetrics(int)
 */
public void setDescent (int descent) {
	checkLayout();
	if (descent < -1) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	if (this.descent == descent) return;
	freeRuns();
	this.descent = descent;
}

/** 
 * Sets the default font which will be used by the receiver
 * to draw and measure text. If the
 * argument is null, then a default font appropriate
 * for the platform will be used instead. Note that a text
 * style can override the default font.
 *
 * @param font the new font for the receiver, or null to indicate a default font
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the font has been disposed</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public void setFont (Font font) {
	checkLayout ();
	if (font != null && font.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	Font oldFont = this.font;
	if (oldFont == font) return;
	this.font = font;
	if (oldFont != null && oldFont.equals(font)) return;
	freeRuns();
	XFontStruct fontStruct = getFontHeigth(font != null ? font : device.systemFont);
	defaultAscent = fontStruct.ascent;
	defaultDescent = fontStruct.descent;
}

/**
 * Sets the indent of the receiver. This indent it applied of the first line of 
 * each paragraph.  
 *
 * @param indent new indent
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @since 3.2
 */
public void setIndent (int indent) {
	checkLayout();
	if (indent < 0) return;
	if (this.indent == indent) return;
	freeRuns();
	this.indent = indent;
}

/**
 * Sets the justification of the receiver. Note that the receiver's
 * width must be set in order to use justification. 
 *
 * @param justify new justify
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @since 3.2
 */
public void setJustify (boolean justify) {
	checkLayout();
	if (this.justify == justify) return;
	freeRuns();
	this.justify = justify;
}

/**
 * Sets the orientation of the receiver, which must be one
 * of <code>SWT.LEFT_TO_RIGHT</code> or <code>SWT.RIGHT_TO_LEFT</code>.
 *
 * @param orientation new orientation style
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public void setOrientation (int orientation) {
	checkLayout();
	int mask = SWT.RIGHT_TO_LEFT | SWT.LEFT_TO_RIGHT;
	orientation &= mask;
	if (orientation == 0) return;
	if ((orientation & SWT.LEFT_TO_RIGHT) != 0) orientation = SWT.LEFT_TO_RIGHT;
	this.orientation = orientation;
}

/**
 * Sets the line spacing of the receiver.  The line spacing
 * is the space left between lines.
 *
 * @param spacing the new line spacing 
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the spacing is negative</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public void setSpacing (int spacing) {
	checkLayout();
	if (spacing < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	if (this.lineSpacing == spacing) return;
	freeRuns();
	this.lineSpacing = spacing;
}

/**
 * Sets the offsets of the receiver's text segments. Text segments are used to
 * override the default behaviour of the bidirectional algorithm.
 * Bidirectional reordering can happen within a text segment but not 
 * between two adjacent segments.
 * <p>
 * Each text segment is determined by two consecutive offsets in the 
 * <code>segments</code> arrays. The first element of the array should 
 * always be zero and the last one should always be equals to length of
 * the text.
 * </p>
 * 
 * @param segments the text segments offset
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public void setSegments(int[] segments) {
	checkLayout();
	if (this.segments == null && segments == null) return;
	if (this.segments != null && segments !=null) {
		if (this.segments.length == segments.length) {
			int i;
			for (i = 0; i <segments.length; i++) {
				if (this.segments[i] != segments[i]) break;
			}
			if (i == segments.length) return;
		}
	}
	freeRuns();
	this.segments = segments;
}

/**
 * Sets the style of the receiver for the specified range.  Styles previously
 * set for that range will be overwritten.  The start and end offsets are
 * inclusive and will be clamped if out of range.
 * 
 * @param style the style
 * @param start the start offset
 * @param end the end offset
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public void setStyle (TextStyle style, int start, int end) {
	checkLayout();
	int length = text.length();
	if (length == 0) return;
	if (start > end) return;
	start = Math.min(Math.max(0, start), length - 1);
	end = Math.min(Math.max(0, end), length - 1);
	int low = -1;
	int high = styles.length;
	while (high - low > 1) {
		int index = (high + low) / 2;
		if (styles[index + 1].start > start) {
			high = index;
		} else {
			low = index;
		}
	}
	if (0 <= high && high < styles.length) {
		StyleItem item = styles[high];
		if (item.start == start && styles[high + 1].start - 1 == end) {
			if (style == null) {
				if (item.style == null) return;
			} else {
				if (style.equals(item.style)) return;
			}
		}
	}
	freeRuns();
	int modifyStart = high;
	int modifyEnd = modifyStart;
	while (modifyEnd < styles.length) {
		if (styles[modifyEnd + 1].start > end) break;
		modifyEnd++;
	}
	if (modifyStart == modifyEnd) {
		int styleStart = styles[modifyStart].start; 
		int styleEnd = styles[modifyEnd + 1].start - 1;
		if (styleStart == start && styleEnd == end) {
			styles[modifyStart].style = style;
			return;
		}
		if (styleStart != start && styleEnd != end) {
			StyleItem[] newStyles = new StyleItem[styles.length + 2];
			System.arraycopy(styles, 0, newStyles, 0, modifyStart + 1);
			StyleItem item = new StyleItem();
			item.start = start;
			item.style = style;
			newStyles[modifyStart + 1] = item;	
			item = new StyleItem();
			item.start = end + 1;
			item.style = styles[modifyStart].style;
			newStyles[modifyStart + 2] = item;
			System.arraycopy(styles, modifyEnd + 1, newStyles, modifyEnd + 3, styles.length - modifyEnd - 1);
			styles = newStyles;
			return;
		}
	}
	if (start == styles[modifyStart].start) modifyStart--;
	if (end == styles[modifyEnd + 1].start - 1) modifyEnd++;
	int newLength = styles.length + 1 - (modifyEnd - modifyStart - 1);
	StyleItem[] newStyles = new StyleItem[newLength];
	System.arraycopy(styles, 0, newStyles, 0, modifyStart + 1);	
	StyleItem item = new StyleItem();
	item.start = start;
	item.style = style;
	newStyles[modifyStart + 1] = item;
	styles[modifyEnd].start = end + 1;
	System.arraycopy(styles, modifyEnd, newStyles, modifyStart + 2, styles.length - modifyEnd);
	styles = newStyles;
}

/**
 * Sets the receiver's tab list. Each value in the tab list specifies
 * the space in pixels from the origin of the text layout to the respective
 * tab stop.  The last tab stop width is repeated continuously.
 * 
 * @param tabs the new tab list
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public void setTabs (int[] tabs) {
	checkLayout();
	if (this.tabs == null && tabs == null) return;
	if (this.tabs != null && tabs !=null) {
		if (this.tabs.length == tabs.length) {
			int i;
			for (i = 0; i <tabs.length; i++) {
				if (this.tabs[i] != tabs[i]) break;
			}
			if (i == tabs.length) return;
		}
	}
	freeRuns();
	this.tabs = tabs;
} 

/**
 * Sets the receiver's text.
 *<p>
 * Note: Setting the text also clears all the styles. This method 
 * returns without doing anything if the new text is the same as 
 * the current text.
 * </p>
 * 
 * @param text the new text
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the text is null</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 */
public void setText (String text) {
	checkLayout();
	if (text == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	if (text.equals(this.text)) return;
	freeRuns();
	this.text = text;
	styles = new StyleItem[2];
	styles[0] = new StyleItem();
	styles[1] = new StyleItem();	
	styles[1].start = text.length();
}

/**
 * Sets the line width of the receiver, which determines how
 * text should be wrapped and aligned. The default value is
 * <code>-1</code> which means wrapping is disabled.
 *
 * @param width the new width 
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the width is <code>0</code> or less than <code>-1</code></li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_GRAPHIC_DISPOSED - if the receiver has been disposed</li>
 * </ul>
 * 
 * @see #setAlignment(int)
 */
public void setWidth (int width) {
	checkLayout();
	if (width < -1 || width == 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	if (this.wrapWidth == width) return;
	freeRuns();
	this.wrapWidth = width;
}

/**
 * Returns a string containing a concise, human-readable
 * description of the receiver.
 *
 * @return a string representation of the receiver
 */
public String toString () {
	if (isDisposed()) return "TextLayout {*DISPOSED*}";
	return "TextLayout {}";
}
}