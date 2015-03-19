import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;

import ee.ioc.cs.vsle.api.Connection;
import ee.ioc.cs.vsle.vclass.ClassField;
import ee.ioc.cs.vsle.vclass.ClassPainter;
import ee.ioc.cs.vsle.vclass.GObj;
import ee.ioc.cs.vsle.vclass.Port;
import ee.ioc.cs.vsle.vclass.RelObj;

public class TransitionPainter extends ClassPainter {
	private static double arcProportion = 0.1;
	
	private static Color arcColor = Color.BLACK;
	private static Color conditionColor = Color.MAGENTA;
	private static Color actionColor = Color.BLUE;

	@Override
	public void paint(Graphics2D graphics, float scale) {
		Color origColor = graphics.getColor();
		
		if ( ((RelObj) vclass).getStartPort() == ((RelObj) vclass).getEndPort() ) {
			/* Case of the reflective transition */
			GObj stateObj = ((RelObj)vclass).getEndPort().getObject();
			double stateWidth = stateObj.getRealWidth();
			double stateHeight = stateObj.getRealHeight();
			Point2D.Double stateCenter = 
					new Point2D.Double(stateObj.getCenterX(), stateObj.getCenterY());
			EllipticalArc stateEllipse = 
					new EllipticalArc(stateCenter, stateWidth/2, stateHeight/2, 0);

			// Pick a direction for this looping transition arc
			// Collect external and reflective transitions that are connected to state
			ArrayList<RelObj> extIn = new ArrayList<RelObj>();
			ArrayList<RelObj> extOut = new ArrayList<RelObj>();
			ArrayList<RelObj> reflective = new ArrayList<RelObj>();
			Port statePort = stateObj.getPortList().get(0);
			for (Connection c : statePort.getConnections()) {
				if (c.getBeginPort() == statePort) {
					RelObj ro = (RelObj) c.getEndPort().getObject();
					if (ro.getStartPort() == ro.getEndPort()) {
						reflective.add(ro);
					} else {
						extOut.add(ro);
					}
				} else {
					RelObj ro = (RelObj) c.getBeginPort().getObject();
					if (ro.getStartPort() == ro.getEndPort()) {
					// Do Nothing
					} else {
						extIn.add(ro);
					}
				}
			}
			// Detect occupied directions
			ArrayList<Double> occupiedDirs = new ArrayList<Double>(extIn.size()+extOut.size());
			for (RelObj ro : extIn) {
				occupiedDirs.add(entryAngle(ro, false));
			}
			for (RelObj ro : extOut) {
				occupiedDirs.add(entryAngle(ro, true));
			}
			Collections.sort(occupiedDirs);
			int slotCount = Math.max(occupiedDirs.size(),1);
			double[] slotSizes = new double[slotCount];

			if (occupiedDirs.size()==0) {
				slotSizes[0] = 2*Math.PI;
			} else {
				for (int i=0; i<occupiedDirs.size(); i++) {
					double curDir = occupiedDirs.get(i);
					double nextDir = i==occupiedDirs.size()-1 
							? occupiedDirs.get(0) : occupiedDirs.get(i+1);
					if (nextDir<=curDir) {
						slotSizes[i]=2*Math.PI+nextDir-curDir;
					} else {
						slotSizes[i]=nextDir-curDir;
					}
				}
			}
			
			// Divide reflective transitions among the slots between incoming and 
			// outgoing transitions
			// Assume that one reflective transition takes Pi/5 space
			double reflectionSize = Math.PI/5;
			int[] slotUsage = new int[slotCount];
			for (int i=0; i<reflective.size(); i++) {
				// Find a slot that has most space
				double biggestSpace = 0;
				int biggestSpaceIndex = 0;
				for (int s=0; s<slotCount; s++) {
					double roomInSpace = slotSizes[s]-slotUsage[s]*reflectionSize;
					if (biggestSpace<roomInSpace) {
						biggestSpace = roomInSpace;
						biggestSpaceIndex = s;
					}
				}
				// Add usage to biggest space
				slotUsage[biggestSpaceIndex]++;
			}
			
			// Pick the direction for the current reflective transition
			double arcAngle = 0;
			int lastSlot = 0;
			int lastSlotUsage = 0;
			for (int i=0; i<reflective.size(); i++) {
				if (lastSlotUsage<slotUsage[lastSlot]) {
					// Use current slot
					lastSlotUsage++;
				} else {
					lastSlot++;
					// Advance to next usable slot
					while (slotUsage[lastSlot]==0) {
						lastSlot++;
						if (lastSlot>=slotCount) {
							// ERROR occurred
							return;
						}
					}
					lastSlotUsage = 1;
				}
				if (reflective.get(i)==vclass) {
					double roomPerArc = (slotSizes[lastSlot]-0.7)/(slotUsage[lastSlot]);
					if (occupiedDirs.size()==0) {
						arcAngle = 0 + 0.3
								+lastSlotUsage*roomPerArc - roomPerArc/2;
					} else {
						arcAngle = occupiedDirs.get(lastSlot)+0.3
								+lastSlotUsage*roomPerArc - roomPerArc/2;
					}
					break;
				}
			}
			double arcWidth = Math.max(25, Math.min(stateHeight,stateWidth));
			double arcHeight = Math.max(25, arcWidth*0.7);
			Point2D.Double arcCenter = new Point2D.Double(
					stateCenter.x + stateWidth/2*Math.cos(arcAngle) + arcWidth*0.4*Math.cos(arcAngle), 
					stateCenter.y + stateHeight/2*Math.sin(arcAngle) + arcWidth*0.4*Math.sin(arcAngle));

			// Draw the arc
			graphics.setColor(arcColor);
			double fromAngle = Math.PI+0.1;
			double toAngle = Math.PI-0.1;
			EllipticalArc mainArc = new EllipticalArc(arcCenter, 
					arcWidth/2, arcHeight/2, arcAngle, fromAngle, toAngle, false);

			fromAngle = findIntersectionAngle(mainArc, mainArc.eta1, mainArc.eta2-Math.PI, stateEllipse);
			toAngle = findIntersectionAngle(mainArc, mainArc.eta1+Math.PI, mainArc.eta2, stateEllipse);
			mainArc = new EllipticalArc(arcCenter, 
					arcWidth/2, arcHeight/2, arcAngle, fromAngle, toAngle, false);
			graphics.draw(mainArc);

			// Draw the arrow
			Point2D.Double p1 = mainArc.pointAt(toAngle-0.1, null);
			Point2D.Double p2 = mainArc.pointAt(toAngle, null);
			double tailAngle = Math.atan2(p2.y-p1.y, p2.x-p1.x);
			p1 = mainArc.pointAt(toAngle, null);
			p2.x = p1.x - 12*Math.cos(tailAngle-0.5);
			p2.y = p1.y - 12*Math.sin(tailAngle-0.5);
			Line2D.Double arrowLline = new Line2D.Double(p1, p2);
			graphics.draw(arrowLline);
			p2.x = p1.x - 12*Math.cos(tailAngle+0.5);
			p2.y = p1.y - 12*Math.sin(tailAngle+0.5);
			Line2D.Double arrowRline = new Line2D.Double(p1, p2);
			graphics.draw(arrowRline);

			// Condition & action
			ClassField conditionField = vclass.getField("condition");
			String conditionString = conditionField.getValue();
			ClassField actionField = vclass.getField("action");
			String actionString = actionField.getValue();

			// Find condition size
			double conHeight = 0;
			double conWidth = 0;
			if (conditionString != null) {
				java.awt.font.FontRenderContext frc = graphics.getFontRenderContext();
				Rectangle2D r = graphics.getFont().getStringBounds( 
						conditionString, 0, conditionString.length(), frc );
				conHeight = r.getHeight();
				conWidth = r.getWidth();
			}

			// Find action size
			double actHeight = 0;
			double actWidth = 0;
			if (actionString != null) {
				java.awt.font.FontRenderContext frc = graphics.getFontRenderContext();
				Rectangle2D r = graphics.getFont().getStringBounds( 
						actionString, 0, actionString.length(), frc );
				actHeight = r.getHeight();
				actWidth = r.getWidth();
			}
			int separation = 3;
			double textHeight = conHeight+separation+actHeight;
			double textWidth = Math.max(conWidth, actWidth);

			// Show condition and action
			Point2D.Double textCorner = mainArc.pointAt(0, null);
			textCorner.x += (0.5*textWidth+3)*(Math.cos(arcAngle)-1);
			textCorner.y += 0.5*textHeight*(Math.sin(arcAngle)-1);
			
			if (conditionString != null) {
				graphics.setColor(conditionColor);
				graphics.drawString(conditionString, 
						(int) textCorner.x, (int) (textCorner.y+conHeight));
			}
			if (actionString != null) {
				graphics.setColor(actionColor);
				graphics.drawString(actionString, 
						(int) textCorner.x, (int) (textCorner.y+separation+textHeight));
			}
			// Change the location of original object
			Rectangle box = mainArc.getBounds();
			vclass.setX(box.x);
			vclass.setY(box.y);
			vclass.setWidth(box.width);
			vclass.setHeight(box.height);
			
		} else { /* Case of a normal transition (connects different states) */
			GObj fromObj = ((RelObj)vclass).getStartPort().getObject();
			GObj toObj = ((RelObj)vclass).getEndPort().getObject();
			Point2D.Double fromCenter = 
					new Point2D.Double(fromObj.getCenterX(), fromObj.getCenterY());
			EllipticalArc fromEllipse = new EllipticalArc(fromCenter, 
					fromObj.getRealWidth()/2, fromObj.getRealHeight()/2, 0);
			
			Point2D.Double toCenter = 
					new Point2D.Double(toObj.getCenterX(), toObj.getCenterY());
			EllipticalArc toEllipse = new EllipticalArc(toCenter, 
					toObj.getRealWidth()/2, toObj.getRealHeight()/2, 0);

			double distance = fromCenter.distance(toCenter);
			Point2D.Double center = new Point2D.Double(
					(fromCenter.x+toCenter.x)/2, (fromCenter.y+toCenter.y)/2);
			double angle = vclass.getAngle();

			// Draw the arc
			graphics.setColor(arcColor);
			double fromAngle = Math.PI;
			double toAngle = 2*Math.PI;
			EllipticalArc mainArc = new EllipticalArc(center, distance/2, 
					distance/2*arcProportion, angle, fromAngle, toAngle, false);
			
			fromAngle = findIntersectionAngle(mainArc, fromEllipse);
			toAngle = findIntersectionAngle(mainArc, toEllipse);
			mainArc = new EllipticalArc(center, distance/2, 
					distance/2*arcProportion, angle, fromAngle, toAngle, false);
			graphics.draw(mainArc);

			// Draw the arrow
			Point2D.Double p1 = mainArc.pointAt(toAngle-0.1, null);
			Point2D.Double p2 = mainArc.pointAt(toAngle+0.1, null);
			double tailAngle = Math.atan2(p2.y-p1.y, p2.x-p1.x);
			p1 = mainArc.pointAt(toAngle, null);
			p2.x = p1.x - 12*Math.cos(tailAngle-0.5);
			p2.y = p1.y - 12*Math.sin(tailAngle-0.5);
			Line2D.Double arrowLline = new Line2D.Double(p1, p2);
			graphics.draw(arrowLline);
			p2.x = p1.x - 12*Math.cos(tailAngle+0.5);
			p2.y = p1.y - 12*Math.sin(tailAngle+0.5);
			Line2D.Double arrowRline = new Line2D.Double(p1, p2);
			graphics.draw(arrowRline);

			// Display condition & action
			ClassField conditionField = vclass.getField("condition");
			String conditionString = conditionField.getValue();
			ClassField actionField = vclass.getField("action");
			String actionString = actionField.getValue();
			
			// Find condition size
			double conHeight = 0;
			double conWidth = 0;
			if (conditionString != null) {
				java.awt.font.FontRenderContext frc = graphics.getFontRenderContext();
				Rectangle2D r = graphics.getFont().getStringBounds( 
						conditionString, 0, conditionString.length(), frc );
				conHeight = r.getHeight();
				conWidth = r.getWidth();
			}

			// Find action size
			double actHeight = 0;
			double actWidth = 0;
			
			// Show condition and action
			Point2D.Double textCorner = new Point2D.Double();
			textCorner.x = center.x - 
					Math.max(conWidth, actWidth)/2 + distance/8*Math.sin(angle);
			textCorner.y = center.y - 
					(conHeight+actHeight)/2 - distance/8*Math.cos(angle);
			
			if (conditionString != null) {
				graphics.setColor(conditionColor);
				graphics.drawString(conditionString, (int) textCorner.x, (int) textCorner.y);
			}
			if (actionString != null) {
				graphics.setColor(actionColor);
				graphics.drawString(actionString, 
						(int) textCorner.x, (int) (textCorner.y+conHeight+3));
			}
		}
		// Remove original graphics
		vclass.getShapes().clear();
		// Restore old color
		graphics.setColor(origColor);
	}

	/**
	 * Compute the angle from where the transition arc enters or exits the state
	 */
	private double entryAngle(RelObj ro, boolean exits) {
		GObj fromObj = ro.getStartPort().getObject();
		GObj toObj = ro.getEndPort().getObject();
		Point2D.Double fromCenter = 
				new Point2D.Double(fromObj.getCenterX(), fromObj.getCenterY());
		Point2D.Double toCenter = 
				new Point2D.Double(toObj.getCenterX(), toObj.getCenterY());
		double distance = fromCenter.distance(toCenter);
		Point2D.Double center = new Point2D.Double(
				(fromCenter.x+toCenter.x)/2, (fromCenter.y+toCenter.y)/2);
		double angle = ro.getAngle();
		double fromAngle = Math.PI;
		double toAngle = 2*Math.PI;
		EllipticalArc mainArc = new EllipticalArc(center, 
				distance/2, distance/2*arcProportion, angle, fromAngle, toAngle, false);
		EllipticalArc stateEllipse;
		if (exits) {
			stateEllipse = new EllipticalArc(fromCenter, 
					fromObj.getRealWidth()/2, fromObj.getRealHeight()/2, 0);
		} else {
			stateEllipse = new EllipticalArc(toCenter, 
					toObj.getRealWidth()/2, toObj.getRealHeight()/2, 0);
		}
		if ((angle = findIntersectionAngle(stateEllipse, 0, 2d/3*Math.PI, mainArc)) != -100) {
			return angle;
		} else if ((angle = findIntersectionAngle(stateEllipse, 2d/3*Math.PI, 4d/3*Math.PI, mainArc)) != -100) {
			return angle;
		} else{
			return findIntersectionAngle(stateEllipse, 4d/3*Math.PI, 2d*Math.PI, mainArc);
		}
	}
	
	/**
	 * Find an angle of arc towards intersection with EllipticalArc target. 
	 * returns intersection angle closest to the start of the arc.
	 * The angular length of the arc should not exceed Pi
	 */
	private double findIntersectionAngle(EllipticalArc arc, EllipticalArc target) {
		return findIntersectionAngle(arc, arc.eta1, arc.eta2, target);
	}

	/**
	 * Find the angle of arc towards intersection with ellipse. 
	 * returns intersection angle closest to the startAngle of the arc.
	 * endAngle-startAngle should not exceed Pi
	 */
	private double findIntersectionAngle(EllipticalArc arc, 
			double startAngle, double endAngle, EllipticalArc target) {
		double limit = 1;
		double segmentLength = 0;
		do {
			double midAngle = (startAngle+endAngle)/2;
			// Try first half
			Point2D.Double p1 = arc.pointAt(startAngle, null);
			Point2D.Double p2 = arc.pointAt(midAngle, null);
			if (target.intersectArc(p1.x, p1.y, p2.x, p2.y)) {
				endAngle = midAngle;
				segmentLength = p1.distance(p2);
			} else {
				p1 = arc.pointAt(endAngle, null);
				if (target.intersectArc(p1.x, p1.y, p2.x, p2.y)) {
					startAngle = midAngle;
					segmentLength = p1.distance(p2);
				} else {  // There was no intersection
					return -100;
				}
			}
		} while (segmentLength > limit);
		return (startAngle+endAngle)/2;
	}
}
