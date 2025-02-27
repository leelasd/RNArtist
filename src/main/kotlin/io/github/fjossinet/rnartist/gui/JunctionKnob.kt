package io.github.fjossinet.rnartist.gui

import io.github.fjossinet.rnartist.Mediator
import io.github.fjossinet.rnartist.core.layout
import io.github.fjossinet.rnartist.core.model.*
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D


class JunctionKnob( val mediator: Mediator, val junction: JunctionDrawing) {

    val connectors = mutableListOf<JunctionConnector>()
    private val arrows = mutableListOf<JunctionArrow>()
    var connectorRadius:Double = 0.0
    //lateinit var detailsButton:DetailsButton

    init {
        this.update()
    }

    fun getStructuralDomain(): StructuralDomainDrawing {
        return this.junction
    }

    fun update() {
        this.connectorRadius = (if (junction.circle.width < 100.0)  junction.circle.width else 100.0)*Math.PI/(16.0*3.5)
        this.connectors.clear()
        this.arrows.clear()
        if (this.junction.junctionType != JunctionType.ApicalLoop) {
            val connector = JunctionConnector(
                ConnectorId.s,
                false,
                junction.center.x,
                junction.center.y + (if (junction.circle.width < 100.0)  junction.circle.width else 100.0)  / 2.0 * 2.0 / 3.0,
                this
            )
            this.connectors.add(connector)
            for (i in 1 until 16) {
                val p = rotatePoint(
                    connector.center,
                    junction.center,
                    i * 360.0 / 16.0
                )
                val _connector = JunctionConnector(getConnectorId(i), false, p.x, p.y, this)

                this.connectors.add(_connector);
            }
            this.arrows.addAll(arrayListOf(Up(this), Down(this), Left(this), Right(this)));
            this.loadJunctionLayout()
            //this.detailsButton =  DetailsButton(this)
        }
        else {
            this.arrows.addAll(arrayListOf(Up(this), Down(this)));
            //this.detailsButton =  DetailsButton(this)
        }
    }

    fun draw(g: Graphics2D, at:AffineTransform) {
        val previousColor = g.color

        for (c in this.connectors)
            c.draw(g, at)

        for (arrow in this.arrows)
            arrow.draw(g, at)

        //this.detailsButton.draw(g, at)

        g.color = previousColor
    }

    fun contains(x: Double, y: Double, at: AffineTransform): Boolean {
        /*if (at.createTransformedShape(this.detailsButton.innerCircle).contains(x, y)) {
            this.detailsButton.mouseCliked()
            return true;
        }*/
        for (connector in this.connectors) {
            if (!connector.isInId && at.createTransformedShape(connector.circle).contains(x, y)) {
                connector.mouseClicked()
                return true;
            }
        }
        for (arrow in this.arrows) {
            if (arrow.contains(x, y, at)) {
                arrow.mouseClicked()
                return true;
            }
        }
        return false
    }

    fun getJunctionLayout(): List<ConnectorId> {
        val layout = mutableListOf<ConnectorId>()
        //first we search the circle for the InId
        var startIndex:Int = 0
        this.connectors.forEach { connector ->
            if (connector.isInId) {
                startIndex = this.connectors.indexOf(connector)
            }
        }
        var currentPos = 0
        while (currentPos <= 15) {
            currentPos++
            if (this.connectors[(startIndex+currentPos)%16].selected) {
                layout.add(getConnectorId(currentPos))
            }
        }
        return layout
    }

    private fun loadJunctionLayout() {
        this.clear()
        this.connectors.forEach { connector ->
            if (connector.connectorId == junction.inId) {
                connector.isInId = true
                connector.selected = false
            } else {
                junction.connectedJunctions.keys.toMutableList().forEach { connectorId ->
                    if (connector.connectorId == connectorId)
                        connector.selected = true
                }
            }
        }
    }

    private fun clear() {
        this.connectors.forEach { connector ->
            connector.isInId = false
            connector.selected = false
        }
    }

}

class JunctionConnector(val connectorId: ConnectorId, var selected:Boolean = false, centerX:Double, centerY:Double, private val knob:JunctionKnob) {

    var isInId = false
    var circle:Ellipse2D
    var strokeWidth = 0.5
    val center
        get() = Point2D.Double(this.circle.x+knob.connectorRadius, this.circle.y+knob.connectorRadius)

    init {
        this.circle = Ellipse2D.Double(centerX- knob.connectorRadius, centerY-knob.connectorRadius,knob.connectorRadius*2,knob.connectorRadius*2)
    }

    fun draw(g: Graphics2D, at: AffineTransform) {
        if (isInId)
            return;
        /*else if (selected && this.knob.junction.drawingConfiguration.params.containsKey(DrawingConfigurationParameter.FullDetails.toString()) && this.knob.junction.drawingConfiguration.params[DrawingConfigurationParameter.FullDetails.toString()].equals("true"))
            g.color = Color.GREEN
        else if (selected && this.knob.junction.drawingConfiguration.params.containsKey(DrawingConfigurationParameter.FullDetails.toString()) && this.knob.junction.drawingConfiguration.params[DrawingConfigurationParameter.FullDetails.toString()].equals("false"))
            g.color = Color.RED
        else*/
        if (selected)
            g.color = Color.DARK_GRAY
        else
            g.color = Color.LIGHT_GRAY

        g.fill(at.createTransformedShape(this.circle))
        g.color = g.color.darker()
        g.draw(at.createTransformedShape(this.circle))
    }

    fun mouseClicked() {
        var selectedCount = knob.connectors.count { it.selected }
        if (selectedCount < knob.junction.junctionType.value - 1 && !this.isInId ) {
            this.selected = !this.selected
        } else if (selectedCount >= knob.junction.junctionType.value - 1 && !this.isInId ) { //we can only unselect
            this.selected = false
        }
        //after the click, if we have the selected circles corresponding to helixCount-1 (-1 since the inner helix in red doesn't count)
        selectedCount = knob.connectors.count { it.selected }
        if (selectedCount == knob.junction.junctionType.value - 1) {
            this.knob.junction.applyLayout(layout {
                junction {
                    name =  knob.junction.name
                    out_ids = knob.getJunctionLayout().map { it.toString() }.joinToString(separator = " ")
                }
            })
            this.knob.mediator.drawingDisplayed.get()!!.knobs.forEach {
                it.update()
            }
        }
        knob.mediator.canvas2D.repaint()
    }
}

interface JunctionArrow {

    fun mouseClicked()

    fun mouseReleased()

    fun contains(x:Double,y:Double,at: AffineTransform):Boolean

    fun draw(g: Graphics2D, at: AffineTransform)

}

abstract class AbstractJunctionArrow:JunctionArrow {
    lateinit var shape: Path2D
    var color = Color.LIGHT_GRAY

    override fun contains(x:Double,y:Double,at: AffineTransform):Boolean {
        return at.createTransformedShape(this.shape).contains(x,y)
    }

    override fun draw(g: Graphics2D, at: AffineTransform) {
        g.color = this.color
        g.fill(at.createTransformedShape(this.shape))
        g.color = g.color.darker()
        g.draw(at.createTransformedShape(this.shape))
    }
}

class Up(private val knob:JunctionKnob):AbstractJunctionArrow() {

    init {
        this.shape = Path2D.Double()
        if (knob.junction.junctionType == JunctionType.ApicalLoop) {
            this.shape.moveTo(knob.junction.center.x, knob.junction.center.y - 8 * knob.connectorRadius)
            this.shape.lineTo(
                knob.junction.center.x - knob.connectorRadius * 3,
                knob.junction.center.y - 3*knob.connectorRadius
            )
            this.shape.lineTo(
                knob.junction.center.x + knob.connectorRadius * 3,
                knob.junction.center.y - 3*knob.connectorRadius
            )
        } else {
            this.shape.moveTo(knob.junction.center.x, knob.junction.center.y - 4 * knob.connectorRadius)
            this.shape.lineTo(
                knob.junction.center.x - knob.connectorRadius * 1.5,
                knob.junction.center.y - 2 * knob.connectorRadius
            )
            this.shape.lineTo(
                knob.junction.center.x + knob.connectorRadius * 1.5,
                knob.junction.center.y - 2 * knob.connectorRadius
            )
        }
        this.shape.closePath()
    }

    override fun draw(g: Graphics2D, at: AffineTransform) {
        g.color = this.color
        g.fill(at.createTransformedShape(this.shape))
        g.color = g.color.darker()
        g.draw(at.createTransformedShape(this.shape))
    }

    override fun mouseClicked() {
        this.knob.junction.applyLayout(layout {
            junction {
                name =  knob.junction.name
                radius = knob.junction.radius * 1.1
            }
        })

        this.knob.mediator.drawingDisplayed.get()!!.knobs.forEach {
            it.update()
        }
        this.knob.mediator.canvas2D.repaint()
    }

    override fun mouseReleased() {

    }

}

class Down(private val knob:JunctionKnob):AbstractJunctionArrow() {

    init {
        this.shape = Path2D.Double()
        if (knob.junction.junctionType == JunctionType.ApicalLoop) {
            this.shape.moveTo(knob.junction.center.x, knob.junction.center.y + 8 * knob.connectorRadius)
            this.shape.lineTo(
                knob.junction.center.x - knob.connectorRadius * 3,
                knob.junction.center.y + 3*knob.connectorRadius
            )
            this.shape.lineTo(
                knob.junction.center.x + knob.connectorRadius * 3,
                knob.junction.center.y + 3*knob.connectorRadius
            )
        } else {
            this.shape.moveTo(knob.junction.center.x, knob.junction.center.y + 4 * knob.connectorRadius)
            this.shape.lineTo(
                knob.junction.center.x - knob.connectorRadius * 1.5,
                knob.junction.center.y + 2 * knob.connectorRadius
            )
            this.shape.lineTo(
                knob.junction.center.x + knob.connectorRadius * 1.5,
                knob.junction.center.y + 2 * knob.connectorRadius
            )
        }
        this.shape.closePath()
    }

    override fun mouseClicked() {
        this.knob.junction.applyLayout(layout {
            junction {
                name =  knob.junction.name
                radius = knob.junction.radius * 0.9
            }
        })
        this.knob.mediator.drawingDisplayed.get()!!.knobs.forEach {
            it.update()
        }
        this.knob.mediator.canvas2D.repaint()
    }

    override fun mouseReleased() {
    }

}

class Left(private val knob:JunctionKnob):AbstractJunctionArrow() {

    init {
        this.shape = Path2D.Double()
        this.shape.moveTo(knob.junction.center.x-4*knob.connectorRadius, knob.junction.center.y)
        this.shape.lineTo(knob.junction.center.x-2*knob.connectorRadius, knob.junction.center.y-1.5*knob.connectorRadius)
        this.shape.lineTo(knob.junction.center.x-2*knob.connectorRadius, knob.junction.center.y+1.5*knob.connectorRadius)
        this.shape.closePath()
    }

    override fun mouseClicked() {
        //first we search the connector inID (the red circle
        var inIDIndex:Int = 0
        for (_connector in this.knob.connectors) {
            if (_connector.isInId) {
                inIDIndex = this.knob.connectors.indexOf(_connector)
                break
            }
        }
        if (!this.knob.connectors[(inIDIndex+1)%16].selected) { //if not we do nothing, a selected circle is just on the left
            var currentPos = (inIDIndex+1)%16
            while (currentPos != inIDIndex) {
                if (!this.knob.connectors[currentPos].isInId && this.knob.connectors[currentPos].selected) {
                    this.knob.connectors[currentPos].selected = false
                    this.knob.connectors[if (currentPos-1 == -1) 15 else currentPos-1].selected = true
                }
                currentPos = (currentPos+1)%16
            }
            this.knob.junction.applyLayout(layout {
                junction {
                    name =  knob.junction.name
                    out_ids = knob.getJunctionLayout().map { it.toString() }.joinToString(separator = " ")
                }
            })
            this.knob.mediator.drawingDisplayed.get()!!.knobs.forEach {
                it.update()
            }
            this.knob.mediator.canvas2D.repaint()
        }
    }

    override fun mouseReleased() {
    }

}

class Right(private val knob:JunctionKnob):AbstractJunctionArrow() {

    init {
        this.shape = Path2D.Double()
        this.shape.moveTo(knob.junction.center.x+4*knob.connectorRadius, knob.junction.center.y)
        this.shape.lineTo(knob.junction.center.x+2*knob.connectorRadius, knob.junction.center.y-1.5*knob.connectorRadius)
        this.shape.lineTo(knob.junction.center.x+2*knob.connectorRadius, knob.junction.center.y+1.5*knob.connectorRadius)
        this.shape.closePath()
    }

    override fun mouseClicked() {
        //first we search the connector inID (the red circle)
        var inIDIndex:Int = 0
        for (_connector in this.knob.connectors) {
            if (_connector.isInId) {
                inIDIndex = this.knob.connectors.indexOf(_connector)
                break
            }
        }
        if (!this.knob.connectors[if (inIDIndex-1 == -1) 15 else inIDIndex-1].selected) { //if not we do nothing, a selected circle is just on the right
            var currentPos = if (inIDIndex-1 == -1) 15 else inIDIndex-1
            while (currentPos != inIDIndex) {
                if (!this.knob.connectors[currentPos].isInId && this.knob.connectors[currentPos].selected) {
                    this.knob.connectors[currentPos].selected = false
                    this.knob.connectors[(currentPos+1)%16].selected = true
                }
                currentPos = if (currentPos-1 == -1) 15 else currentPos-1
            }
            this.knob.junction.applyLayout(layout {
                junction {
                    name =  knob.junction.name
                    out_ids = knob.getJunctionLayout().map { it.toString() }.joinToString(separator = " ")
                }
            })
            this.knob.mediator.drawingDisplayed.get()!!.knobs.forEach {
                it.update()
            }
            this.knob.mediator.canvas2D.repaint()
        }
    }

    override fun mouseReleased() {

    }

}