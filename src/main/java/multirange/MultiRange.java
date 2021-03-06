/*
 * Copyright (c) 2013, 2015 ControlsFX
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *     * Neither the name of ControlsFX, any associated website, nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL CONTROLSFX BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package multirange;

import com.sun.javafx.css.converters.BooleanConverter;
import com.sun.javafx.css.converters.EnumConverter;
import com.sun.javafx.css.converters.SizeConverter;
import javafx.beans.property.*;
import javafx.css.*;
import javafx.geometry.Orientation;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;
import multirange.behavior.MultiRangeBehavior;
import multirange.skin.MultiRangeSkin;

import java.util.*;

/**
 * The MultiRange control is simply a JavaFX Slider control with support
 * for multiple 'thumbs', rather than one. A thumb is the non-technical name for the
 * draggable area inside the Slider / MultiRange that allows for a value to be
 * set.
 *
 * <p>Because the MultiRange has multiple thumbs, it also has a few additional rules
 * and user interactions:
 *
 * <ol>
 *   <li>The 'lower value' thumb can not move past the 'higher value' thumb of any range.
 *   <li>The 'high value' of any thumb cannot move past the 'low value' of any range.
 *   <li>The 'low value' of any thumb cannot move below the 'high value' of any range.
 *   <li>The area between any low and high values represents the allowable range.
 *       For example, if the low value is 2 and the high value is 8, then the
 *       allowable range is between 2 and 8.
 *   <li>The allowable range area is rendered differently.
 *   <li>A range can be removed by a right click over the itself. A minimum of one range
 *       must be in the slider at any time.
 * </ol>
 *
 *
 * <h3>Code Samples</h3>
 *
 * <pre>
 * {@code
 * final MultiRange multiRange = new MultiRange(0, 100);
 * multiRange.setShowTickMarks(true);
 * multiRange.setShowTickLabels(true);
 * multiRange.setBlockIncrement(10);}</pre>
 *
 * <p>This code creates a MultiRange with a min value of 0, a max value of 100,
 *
 *
 * Created by alberto on 09/01/2017.
 */
public class MultiRange extends Control {

    /**
     * Creates a default (horizontal) multiRange instance using default values of 0.0,
     * 1.0 for min/max respectively.
     */
    public MultiRange() {
        this(0, 1.0);
    }

    /**
     * Instantiates a default (horizontal) multiRange with the specified min/max values.
     *
     * @param min The minimum allowable value that the MultiRange will allow
     * @param max The maximum allowable value that the MultiRange will allow
     */
    public MultiRange(double min, double max) {
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);

        setMin(min);
        setMax(max);

        // Add the first range to the slider with the values min/max for the
        // lower/higher values respectively.
        ranges.add(new Range(currentRangeId.get(), min, max));
    }

    /***************************************************************************
     *                                                                         *
     *                              Properties                                 *
     *                                                                         *
     **************************************************************************/

    private List<Range> ranges = new ArrayList<>();     // ranges in the slider
    private IntegerProperty currentRangeId = new SimpleIntegerProperty(0);  // id of the thumb pressed
    private double thumbWidth = 0.5;    // TODO


    /***************************************************************************
     *                                                                         *
     * Properties copied from Slider (and slightly edited)                     *
     *                                                                         *
     **************************************************************************/

    private DoubleProperty max; // maximum value of the slider
    private DoubleProperty min; // minimum value of the slider

    private BooleanProperty snapToTicks;    // indicates whether the range values should be aligned with the tick marks
    private DoubleProperty majorTickUnit;   // the unit distance between major tick marks
    private IntegerProperty minorTickCount; // the number of minor ticks to place between any two major ticks.

    private ObjectProperty<Orientation> orientation;    // the orientation of the slider horizontal/vertical
    private BooleanProperty showTickLabels;             // whether labels of tick marks should be shown or not
    private BooleanProperty showTickMarks;              // whether the skin implementation should show tick marks

    // StringConverter used to format tick mark labels
    private final ObjectProperty<StringConverter<Number>> tickLabelFormatter = new SimpleObjectProperty<>();


    /***************************************************************************
     *                                                                         *
     * MultiRange methods                                                      *
     *                                                                         *
     * There are some duplicate methods: one is using streams just to learn    *
     * and practise...                                                         *
     * The "no streams" version may be faster!                                 *
     *                                                                         *
     **************************************************************************/

    /**
     * Set a new value for the low/high side of the current range. Before setting the value some checks will be
     * performed to assure that the new value is valid.
     *
     * @param newValue new value of the low/high range
     * @param isLow    whether the updating value is a low or a high
     * @see #setValueNS(double, boolean) for a version without streams (may be faster...)
     */
    private void setValue(double newValue, boolean isLow) {
        Optional<Range> rangeOptional = ranges.stream().filter(r -> r.getId() == currentRangeId.get()).findAny();
        if (rangeOptional.isPresent()) {
            Range range = rangeOptional.get();
            double value = snapValueToTicks(newValue);

            if (isLow && isValidValue(true, range, value)) {
                range.setLow(value);
                setLowValue(value);
            } else if (!isLow && isValidValue(false, range, value)) {
                range.setHigh(value);
                setHighValue(value);
            }

            if (getSkin() != null) {
                requestLayout();
            }
        }
    }

    /**
     * Works like {@link #setValue(double, boolean)} function without using streams.
     */
    private void setValueNS(double newValue, boolean isLow) {
        for (Range range : ranges) {
            if (range.getId() == currentRangeId.get()) {
                double value = snapValueToTicks(newValue);
                if (isLow && isValidValue(true, range, value)) range.setLow(value);
                else if (!isLow && isValidValue(false, range, value)) range.setHigh(value);
                return;
            }
        }
    }

    /**
     * Check if the new value is valid for the given range. A value is valid when:
     * - the low value is lower than the high value
     * - if the updating value is a low: the new value cannot be lower than its left high value (if exists)
     * - if the updating value is a high: the new value cannot be higher than its right low value (if exists)
     *
     * @param isLow    whether the updating value is a low or a high
     * @param range    range being modified
     * @param newValue new value of the low/high range
     * @return whether is a valid value or not
     * @see #isValidValueNS(boolean, Range, double) for a version without streams (may be faster...)
     */
    private boolean isValidValue(boolean isLow, Range range, double newValue) {
        if (newValue < getMin() || newValue > getMax()) {
            return false;
        }
        if (isLow && newValue < range.getHigh() - thumbWidth) {
            return ranges.stream().filter(r -> r.getId() != range.getId())
                    .filter(r -> r.getHigh() < range.getHigh())
                    .filter(r -> r.getHigh() + thumbWidth >= newValue)
                    .count() == 0;
        } else if (!isLow && range.getLow() + thumbWidth < newValue) {
            return ranges.stream().filter(r -> r.getId() != range.getId())
                    .filter(r -> r.getLow() > range.getHigh())
                    .filter(r -> r.getLow() - thumbWidth <= newValue)
                    .count() == 0;
        }
        return false;
    }

    /**
     * Works like {@link #isValidValue(boolean, Range, double)} function without using streams.
     */
    private boolean isValidValueNS(boolean isLow, Range range, double newValue) {
        if (newValue < getMin() || newValue > getMax()) {
            return false;
        }
        if (isLow && newValue < range.getHigh()) {
            for (Range r : ranges) {
                if (r.getId() != range.getId() && r.getHigh() < range.getHigh() && r.getHigh() >= newValue) {
                    return false;
                }
            }
        } else if (!isLow && range.getLow() < newValue) {
            for (Range r : ranges) {
                if (r.getId() != range.getId() && r.getLow() > range.getHigh() && r.getLow() <= newValue) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if all ranges are valid and if any of them is not valid, try to correct it!
     * TODO this is working but... it is not a good solution!
     */
    public void validateValues() {
        for (Range r : ranges) {
            if (!isValidValue(true, r, r.getLow())) {
                r.setLow(r.getHigh() - 300000);
            }
            if (!isValidValue(false, r, r.getHigh())) {
                r.setHigh(r.getLow() + 300000);
            }
        }
    }

    /**
     * Get the low/high value of the range currently selected.
     *
     * @param isLow whether the value to retrieve is the low or the high
     * @return the value
     * @see #getValueNS(boolean) for a version without streams (may be faster...)
     */
    private double getValue(boolean isLow) {
        Optional<Range> rangeOptional = ranges.stream().filter(r -> r.getId() == currentRangeId.get()).findAny();
        if (rangeOptional.isPresent()) {
            if (isLow) {
                return rangeOptional.get().getLow();
            } else {
                return rangeOptional.get().getHigh();
            }
        }
        return -1;
    }

    /**
     * Get the low/high property of the range currently selected.
     *
     * @param isLow whether the value to retrieve is the low or the high
     * @return the property
     */
    private DoubleProperty getValueProperty(boolean isLow) {
        Optional<Range> rangeOptional = ranges.stream().filter(r -> r.getId() == currentRangeId.get()).findAny();
        if (rangeOptional.isPresent()) {
            if (isLow) {
                return rangeOptional.get().lowProperty();
            } else {
                return rangeOptional.get().highProperty();
            }
        }
        return null;
    }

    /**
     * Works like {@link #getValue(boolean)} function without using streams.
     */
    private double getValueNS(boolean isLow) {
        for (Range range : ranges) {
            if (range.getId() == currentRangeId.get()) {
                if (isLow) {
                    return range.getLow();
                } else {
                    return range.getHigh();
                }
            }
        }
        return -1;
    }

    /**
     * Check if a given position is in between any range.
     * This will be used to decide where and how to insert a new range in the slider.
     *
     * @param newPosition the new position
     * @return whether the new position is in between a previously set range or not
     * @see #isInBetweenRangeNS(double) for a version without streams (may be faster...)
     */
    private boolean isInBetweenRange(double newPosition) {
        Optional<Range> rangeOptional = ranges.stream().filter(r -> r.getId() == currentRangeId.get())
                .filter(r -> r.getLow() < newPosition && r.getHigh() > newPosition).findAny();
        return rangeOptional.map(range -> true).orElse(false);
    }

    /**
     * Works like {@link #isInBetweenRange(double)} function without using streams.
     */
    private boolean isInBetweenRangeNS(double newPosition) {
        for (Range range : ranges) {
            if (range.getId() != currentRangeId.get()) {
                if (newPosition > range.getLow() && newPosition < range.getHigh()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Remove the currently selected range!
     * If only one range is set then it cannot be removed.
     *
     * @return true if any range has been removed
     */
    public boolean removeSelectedRange() {
        if (ranges.size() > 1) {
            for (Iterator<Range> i = ranges.iterator(); i.hasNext(); ) {
                Range item = i.next();
                if (item.getId() == currentRangeId.get()) {
                    i.remove();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get the range that corresponds to a given position. It means that the position is bigger than the low
     * position of the range and lower than the high position of the range.
     *
     * @param newPosition position to check
     * @return the range containing the position
     * @see #getRangeForPositionNS(double) for a version without streams (may be faster...)
     */
    private Range getRangeForPosition(double newPosition) {
        Optional<Range> rangeOptional = ranges.stream().filter(r -> r.getLow() <= newPosition && r.getHigh() >= newPosition).findAny();
        return rangeOptional.orElse(null);
    }

    /**
     * Works like {@link #getRangeForPosition(double)} function without using streams.
     */
    private Range getRangeForPositionNS(double newPosition) {
        for (Range r : ranges) {
            if (r.getLow() <= newPosition && r.getHigh() >= newPosition) {
                return r;
            }
        }
        return null;
    }

    /**
     * Get the range with the given id
     *
     * @param id id of the range
     * @return the range with the given id
     */
    public Range getRange(int id) {
        Optional<Range> rangeOptional = ranges.stream().filter(r -> r.getId() == id).findAny();
        return rangeOptional.orElse(null);
    }

    /**
     * Returns the space between the given position and its closest right range.
     *
     * @param newPosition the clicked position
     * @return distance to the low value of its closest right range
     */
    public double getSpaceToRightRange(double newPosition) {
        Range closestRightRange = getTopRightRange();
        boolean found = false;
        for (Range r : ranges) {
            if (r.getLow() >= newPosition && r.getLow() <= closestRightRange.getLow()) {
                closestRightRange = r;
                found = true;
            }
        }

        if (found) {
            return closestRightRange.getLow() - newPosition;
        }

        return getMax() - newPosition;
    }

    /**
     * Get the top right range (the highest range)
     *
     * @return the highest range
     */
    private Range getTopRightRange() {
        Range topRightRange = ranges.get(0);
        for (Range range : ranges) {
            if (range.getHigh() > topRightRange.getHigh()) {
                topRightRange = range;
            }
        }
        return topRightRange;
    }

    /**
     * Returns the space between the given position and its closest left range.
     *
     * @param newPosition the clicked position
     * @return distance to the high value of its closest left range
     */
    public double getSpaceToLeftRange(double newPosition) {
        Range closestLeftRange = getTopLeftRange();
        boolean found = false;
        for (Range r : ranges) {
            if (r.getHigh() <= newPosition && r.getHigh() >= closestLeftRange.getHigh()) {
                closestLeftRange = r;
                found = true;
            }
        }

        if (found) {
            return newPosition - closestLeftRange.getHigh();
        }

        return newPosition - getMin();
    }

    /**
     * Get the top left range (the lowest range)
     *
     * @return the lowest range
     */
    private Range getTopLeftRange() {
        Range topLeftRange = ranges.get(0);
        for (Range range : ranges) {
            if (range.getLow() < topLeftRange.getLow()) {
                topLeftRange = range;
            }
        }
        return topLeftRange;
    }

    /**
     * @param d
     * @return
     */
    private double snapValueToTicks(double d) {
        double d1 = d;
        if (isSnapToTicks()) {
            double d2;
            if (getMinorTickCount() != 0) {
                d2 = getMajorTickUnit() / (double) (Math.max(getMinorTickCount(), 0) + 1);
            } else {
                d2 = getMajorTickUnit();
            }
            int i = (int) ((d1 - getMin()) / d2);
            double d3 = (double) i * d2 + getMin();
            double d4 = (double) (i + 1) * d2 + getMin();
            d1 = Utils.nearest(d3, d1, d4);
        }
        return Utils.clamp(getMin(), d1, getMax());
    }

    /**
     * Update the range with equal id to the given range on the list...
     *
     * @param range range to update!
     */
    public void updateRange(Range range) {
        Optional<Range> rangeOptional = ranges.stream().filter(r -> r.getId() == currentRangeId.get()).findAny();
        if (rangeOptional.isPresent()) {
            int index = ranges.indexOf(rangeOptional.get());
            ranges.set(index, range);
            requestLayout();
        }
    }

    /**
     * Create a new range with the given low-high values and add it to the list. The skin maintain the
     * current id which is bind to the #currentRangeId property.
     *
     * @param low  low value
     * @param high high value
     */
    public void createNewRange(double low, double high) {
        ranges.add(new Range(currentRangeId.get(), low, high));
        requestLayout();
    }

    /**
     * Ensures that min is always < max, that value is always
     * somewhere between the two, and that if snapToTicks is set then the
     * value will always be set to align with a tick mark.
     */
    private void adjustValues() {
        adjustLowValues();
        adjustHighValues();
    }

    private void adjustLowValues() {
        /*
         * We first look if the LowValue is between the min and max.
         */
        if (getLowValue() < getMin() || getLowValue() > getMax()) {
            double value = Utils.clamp(getMin(), getLowValue(), getMax());
            setValue(value, true);
            /*
             * If the LowValue seems right, we check if it's not superior to
             * HighValue ONLY if the highValue itself is right. Because it may
             * happen that the highValue has not yet been computed and is
             * wrong, and therefore force the lowValue to change in a wrong way
             * which may end up in an infinite loop.
             */
        } else if (getLowValue() >= getHighValue() && (getHighValue() >= getMin() && getHighValue() <= getMax())) {
            double value = Utils.clamp(getMin(), getLowValue(), getHighValue());
            setValue(value, true);
        }
    }

    private void adjustHighValues() {
        if (getHighValue() < getMin() || getHighValue() > getMax()) {
            setValue(Utils.clamp(getMin(), getHighValue(), getMax()), false);
        } else if (getHighValue() < getLowValue() && (getLowValue() >= getMin() && getLowValue() <= getMax())) {
            setValue(Utils.clamp(getLowValue(), getHighValue(), getMax()), false);
        }
    }

    /***************************************************************************
     *                                                                         *
     * Setters/Getters for the properties (slightly edited)                    *
     *                                                                         *
     **************************************************************************/

    public void setLowRangeValue(double newValue) {
        setValue(newValue, true);
    }

    public double getLowValue() {
        return getValue(true);
    }

    public void setHighRangeValue(double newValue) {
        setValue(newValue, false);
    }

    public double getHighValue() {
        return getValue(false);
    }

    public int getCurrentRangeId() {
        return currentRangeId.get();
    }

    public IntegerProperty currentRangeIdProperty() {
        return currentRangeId;
    }

    public void setCurrentRangeId(int currentRangeId) {
        this.currentRangeId.set(currentRangeId);
    }

    public List<Range> getRanges() {
        return ranges;
    }

    public void setRanges(List<Range> ranges) {
        this.ranges = ranges;
    }

    public DoubleProperty getLowValueProperty() {
        return getValueProperty(true);
    }

    public DoubleProperty getHighValueProperty() {
        return getValueProperty(false);
    }

    /**
     * Sets the maximum value for this Slider.
     *
     * @param value new max value
     */
    public final void setMax(double value) {
        maxProperty().set(value);
    }

    /**
     * @return The maximum value of this slider. 100 is returned if
     * the maximum value has never been set.
     */
    public final double getMax() {
        return max == null ? 100 : max.get();
    }

    /**
     * @return A DoubleProperty representing the maximum value of this Slider.
     * This must be a value greater than {@link #minProperty() min}.
     */
    public final DoubleProperty maxProperty() {
        if (max == null) {
            max = new DoublePropertyBase(100) {
                @Override
                protected void invalidated() {
                    if (get() < getMin()) {
                        setMin(get());
                    }
                    adjustValues();
                }

                @Override
                public Object getBean() {
                    return MultiRange.this;
                }

                @Override
                public String getName() {
                    return "max";
                }
            };
        }
        return max;
    }

    /**
     * Sets the minimum value for this Slider.
     *
     * @param value new min value
     */
    public final void setMin(double value) {
        minProperty().set(value);
    }

    /**
     * @return the minimum value for this Slider. 0 is returned if the minimum
     * has never been set.
     */
    public final double getMin() {
        return min == null ? 0 : min.get();
    }

    /**
     * @return A DoubleProperty representing The minimum value of this Slider.
     * This must be a value less than {@link #maxProperty() max}.
     */
    public final DoubleProperty minProperty() {
        if (min == null) {
            min = new DoublePropertyBase(0) {
                @Override
                protected void invalidated() {
                    if (get() > getMax()) {
                        setMax(get());
                    }
                    adjustValues();
                }

                @Override
                public Object getBean() {
                    return MultiRange.this;
                }

                @Override
                public String getName() {
                    return "min";
                }
            };
        }
        return min;
    }

    // --- low value

    /**
     * The low value property represents the current position of the low value
     * thumb, and is within the allowable range as specified by the
     * {@link #minProperty() min} and {@link #maxProperty() max} properties. By
     * default this value is 0.
     */
    public final DoubleProperty lowValueProperty() {
        return lowValue;
    }

    private DoubleProperty lowValue = new SimpleDoubleProperty(this, "lowValue", 0.0D);

    /**
     * Sets the low value for the range slider, which may or may not be clamped
     * to be within the allowable range as specified by the
     * {@link #minProperty() min} and {@link #maxProperty() max} properties.
     */
    public final void setLowValue(double d) {
        lowValueProperty().set(d);
    }

    // --- high value

    /**
     * The high value property represents the current position of the high value
     * thumb, and is within the allowable range as specified by the
     * {@link #minProperty() min} and {@link #maxProperty() max} properties. By
     * default this value is 100.
     */
    public final DoubleProperty highValueProperty() {
        return highValue;
    }

    private DoubleProperty highValue = new SimpleDoubleProperty(this, "highValue", 100D);

    /**
     * Sets the high value for the range slider, which may or may not be clamped
     * to be within the allowable range as specified by the
     * {@link #minProperty() min} and {@link #maxProperty() max} properties.
     */
    public final void setHighValue(double d) {
        if (!highValueProperty().isBound()) highValueProperty().set(d);
    }

    /**
     * Sets the value of SnapToTicks.
     *
     * @param value
     * @see #snapToTicksProperty()
     */
    public final void setSnapToTicks(boolean value) {
        snapToTicksProperty().set(value);
    }

    /**
     * @return the value of SnapToTicks.
     * @see #snapToTicksProperty()
     */
    public final boolean isSnapToTicks() {
        return snapToTicks != null && snapToTicks.get();
    }

    /**
     * Indicates whether the current low value/high value of the {@code Slider} should always
     * be aligned with the tick marks. This is honored even if the tick marks
     * are not shown.
     *
     * @return A BooleanProperty.
     */
    public final BooleanProperty snapToTicksProperty() {
        if (snapToTicks == null) {
            snapToTicks = new StyleableBooleanProperty(false) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return MultiRange.StyleableProperties.SNAP_TO_TICKS;
                }

                @Override
                public Object getBean() {
                    return MultiRange.this;
                }

                @Override
                public String getName() {
                    return "snapToTicks";
                }
            };
        }
        return snapToTicks;
    }

    /**
     * Sets the unit distance between major tick marks.
     *
     * @param value new major tick unit
     * @see #majorTickUnitProperty()
     */
    public final void setMajorTickUnit(double value) {
        if (value <= 0) {
            throw new IllegalArgumentException("MajorTickUnit cannot be less than or equal to 0.");
        }
        majorTickUnitProperty().set(value);
    }

    /**
     * @return The unit distance between major tick marks.
     * @see #majorTickUnitProperty()
     */
    public final double getMajorTickUnit() {
        return majorTickUnit == null ? 25 : majorTickUnit.get();
    }

    /**
     * The unit distance between major tick marks. For example, if
     * the {@link #minProperty() min} is 0 and the {@link #maxProperty() max} is 100 and the
     * {@link #majorTickUnitProperty() majorTickUnit} is 25, then there would be 5 tick marks: one at
     * position 0, one at position 25, one at position 50, one at position
     * 75, and a final one at position 100.
     * <p>
     * This value should be positive and should be a value less than the
     * span. Out of range values are essentially the same as disabling
     * tick marks.
     *
     * @return A DoubleProperty
     */
    public final DoubleProperty majorTickUnitProperty() {
        if (majorTickUnit == null) {
            majorTickUnit = new StyleableDoubleProperty(25) {
                @Override
                public void invalidated() {
                    if (get() <= 0) {
                        throw new IllegalArgumentException("MajorTickUnit cannot be less than or equal to 0.");
                    }
                }

                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.MAJOR_TICK_UNIT;
                }

                @Override
                public Object getBean() {
                    return MultiRange.this;
                }

                @Override
                public String getName() {
                    return "majorTickUnit";
                }
            };
        }
        return majorTickUnit;
    }


    /**
     * Sets the number of minor ticks to place between any two major ticks.
     *
     * @param value new minor tick count
     * @see #minorTickCountProperty()
     */
    public final void setMinorTickCount(int value) {
        minorTickCountProperty().set(value);
    }

    /**
     * @return The number of minor ticks to place between any two major ticks.
     * @see #minorTickCountProperty()
     */
    public final int getMinorTickCount() {
        return minorTickCount == null ? 3 : minorTickCount.get();
    }

    /**
     * The number of minor ticks to place between any two major ticks. This
     * number should be positive or zero. Out of range values will disable
     * disable minor ticks, as will a value of zero.
     *
     * @return An InterProperty
     */
    public final IntegerProperty minorTickCountProperty() {
        if (minorTickCount == null) {
            minorTickCount = new StyleableIntegerProperty(3) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return MultiRange.StyleableProperties.MINOR_TICK_COUNT;
                }

                @Override
                public Object getBean() {
                    return MultiRange.this;
                }

                @Override
                public String getName() {
                    return "minorTickCount";
                }
            };
        }
        return minorTickCount;
    }

    /**
     * Sets the orientation of the Slider.
     *
     * @param value new orientation
     */
    public final void setOrientation(Orientation value) {
        orientationProperty().set(value);
    }

    /**
     * @return The orientation of the Slider. {@link Orientation#HORIZONTAL} is
     * returned by default.
     */
    public final Orientation getOrientation() {
        return orientation == null ? Orientation.HORIZONTAL : orientation.get();
    }

    /**
     * The orientation of the {@code Slider} can either be horizontal
     * or vertical.
     *
     * @return An Objectproperty representing the orientation of the Slider.
     */
    public final ObjectProperty<Orientation> orientationProperty() {
        if (orientation == null) {
            orientation = new StyleableObjectProperty<Orientation>(Orientation.HORIZONTAL) {
                @Override
                protected void invalidated() {
                    final boolean vertical = (get() == Orientation.VERTICAL);
                    pseudoClassStateChanged(VERTICAL_PSEUDOCLASS_STATE, vertical);
                    pseudoClassStateChanged(HORIZONTAL_PSEUDOCLASS_STATE, !vertical);
                }

                @Override
                public CssMetaData<? extends Styleable, Orientation> getCssMetaData() {
                    return MultiRange.StyleableProperties.ORIENTATION;
                }

                @Override
                public Object getBean() {
                    return MultiRange.this;
                }

                @Override
                public String getName() {
                    return "orientation";
                }
            };
        }
        return orientation;
    }

    /**
     * Sets whether labels of tick marks should be shown or not.
     *
     * @param value
     */
    public final void setShowTickLabels(boolean value) {
        showTickLabelsProperty().set(value);
    }

    /**
     * @return whether labels of tick marks are being shown.
     */
    public final boolean isShowTickLabels() {
        return showTickLabels != null && showTickLabels.get();
    }

    /**
     * Indicates that the labels for tick marks should be shown. Typically a
     * {@link Skin} implementation will only show labels if
     * {@link #showTickMarksProperty() showTickMarks} is also true.
     *
     * @return A BooleanProperty
     */
    public final BooleanProperty showTickLabelsProperty() {
        if (showTickLabels == null) {
            showTickLabels = new StyleableBooleanProperty(false) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return MultiRange.StyleableProperties.SHOW_TICK_LABELS;
                }

                @Override
                public Object getBean() {
                    return MultiRange.this;
                }

                @Override
                public String getName() {
                    return "showTickLabels";
                }
            };
        }
        return showTickLabels;
    }

    /**
     * Specifies whether the {@link Skin} implementation should show tick marks.
     *
     * @param value
     */
    public final void setShowTickMarks(boolean value) {
        showTickMarksProperty().set(value);
    }

    /**
     * @return whether the {@link Skin} implementation should show tick marks.
     */
    public final boolean isShowTickMarks() {
        return showTickMarks != null && showTickMarks.get();
    }

    /**
     * @return A BooleanProperty that specifies whether the {@link Skin}
     * implementation should show tick marks.
     */
    public final BooleanProperty showTickMarksProperty() {
        if (showTickMarks == null) {
            showTickMarks = new StyleableBooleanProperty(false) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return MultiRange.StyleableProperties.SHOW_TICK_MARKS;
                }

                @Override
                public Object getBean() {
                    return MultiRange.this;
                }

                @Override
                public String getName() {
                    return "showTickMarks";
                }
            };
        }
        return showTickMarks;
    }

    /**
     * Gets the value of the property tickLabelFormatter.
     *
     * @return the value of the property tickLabelFormatter.
     */
    public final StringConverter<Number> getLabelFormatter() {
        return tickLabelFormatter.get();
    }

    /**
     * Sets the value of the property tickLabelFormatter.
     *
     * @param value
     */
    public final void setLabelFormatter(StringConverter<Number> value) {
        tickLabelFormatter.set(value);
    }

    /**
     * StringConverter used to format tick mark labels. If null a default will be used.
     *
     * @return a Property containing the StringConverter.
     */
    public final ObjectProperty<StringConverter<Number>> labelFormatterProperty() {
        return tickLabelFormatter;
    }

    /***************************************************************************
     *                                                                         *
     *                         Stylesheet Handling                             *
     *                                                                         *
     **************************************************************************/

    private static final String DEFAULT_STYLE_CLASS = "multi-range";

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return MultiRange.class.getResource("/multirange/multirange.css").toExternalForm();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new MultiRangeSkin(this, new MultiRangeBehavior(this));
    }

    private static class StyleableProperties {

        private static final CssMetaData<MultiRange, Boolean> SHOW_TICK_LABELS =
                new CssMetaData<MultiRange, Boolean>("-fx-show-tick-labels", BooleanConverter.getInstance(), Boolean.FALSE) {

                    @Override
                    public boolean isSettable(MultiRange n) {
                        return n.showTickLabels == null || !n.showTickLabels.isBound();
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public StyleableProperty<Boolean> getStyleableProperty(MultiRange n) {
                        return (StyleableProperty<Boolean>) n.showTickLabelsProperty();
                    }
                };

        private static final CssMetaData<MultiRange, Boolean> SHOW_TICK_MARKS =
                new CssMetaData<MultiRange, Boolean>("-fx-show-tick-marks",
                        BooleanConverter.getInstance(), Boolean.FALSE) {

                    @Override
                    public boolean isSettable(MultiRange n) {
                        return n.showTickMarks == null || !n.showTickMarks.isBound();
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public StyleableProperty<Boolean> getStyleableProperty(MultiRange n) {
                        return (StyleableProperty<Boolean>) n.showTickMarksProperty();
                    }
                };

        private static final CssMetaData<MultiRange, Boolean> SNAP_TO_TICKS =
                new CssMetaData<MultiRange, Boolean>("-fx-snap-to-ticks",
                        BooleanConverter.getInstance(), Boolean.FALSE) {

                    @Override
                    public boolean isSettable(MultiRange n) {
                        return n.snapToTicks == null || !n.snapToTicks.isBound();
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public StyleableProperty<Boolean> getStyleableProperty(MultiRange n) {
                        return (StyleableProperty<Boolean>) n.snapToTicksProperty();
                    }
                };

        private static final CssMetaData<MultiRange, Number> MAJOR_TICK_UNIT =
                new CssMetaData<MultiRange, Number>("-fx-major-tick-unit",
                        SizeConverter.getInstance(), 25.0) {

                    @Override
                    public boolean isSettable(MultiRange n) {
                        return n.majorTickUnit == null || !n.majorTickUnit.isBound();
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public StyleableProperty<Number> getStyleableProperty(MultiRange n) {
                        return (StyleableProperty<Number>) n.majorTickUnitProperty();
                    }
                };

        private static final CssMetaData<MultiRange, Number> MINOR_TICK_COUNT =
                new CssMetaData<MultiRange, Number>("-fx-minor-tick-count",
                        SizeConverter.getInstance(), 3.0) {

                    @SuppressWarnings("deprecation")
                    @Override
                    public void set(MultiRange node, Number value, StyleOrigin origin) {
                        super.set(node, value.intValue(), origin);
                    }

                    @Override
                    public boolean isSettable(MultiRange n) {
                        return n.minorTickCount == null || !n.minorTickCount.isBound();
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public StyleableProperty<Number> getStyleableProperty(MultiRange n) {
                        return (StyleableProperty<Number>) n.minorTickCountProperty();
                    }
                };

        private static final CssMetaData<MultiRange, Orientation> ORIENTATION =
                new CssMetaData<MultiRange, Orientation>("-fx-orientation",
                        new EnumConverter<>(Orientation.class),
                        Orientation.HORIZONTAL) {

                    @Override
                    public Orientation getInitialValue(MultiRange node) {
                        // A vertical Slider should remain vertical 
                        return node.getOrientation();
                    }

                    @Override
                    public boolean isSettable(MultiRange n) {
                        return n.orientation == null || !n.orientation.isBound();
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public StyleableProperty<Orientation> getStyleableProperty(MultiRange n) {
                        return (StyleableProperty<Orientation>) n.orientationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(SHOW_TICK_LABELS);
            styleables.add(SHOW_TICK_MARKS);
            styleables.add(SNAP_TO_TICKS);
            styleables.add(MAJOR_TICK_UNIT);
            styleables.add(MINOR_TICK_COUNT);
            styleables.add(ORIENTATION);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * @return The CssMetaData associated with this class, which may include the
     * CssMetaData of its super classes.
     * @since JavaFX 8.0
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return MultiRange.StyleableProperties.STYLEABLES;
    }

    /**
     * RT-19263
     *
     * @treatAsPrivate implementation detail
     * @since JavaFX 8.0
     * @deprecated This is an experimental API that is not intended for general use and is subject to change in future versions
     */
    @Deprecated
    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    private static final PseudoClass VERTICAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("vertical");
    private static final PseudoClass HORIZONTAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("horizontal");
    
    /**
     * When true, indicates the current value of this Slider is changing.
     * It provides notification that the value is changing. Once the value is
     * computed, it is reset back to false.
     */
    private BooleanProperty lowValueChanging;

    public final void setLowValueChanging(boolean value) {
        lowValueChangingProperty().set(value);
    }

    public final boolean isLowValueChanging() {
        return lowValueChanging != null && lowValueChanging.get();
    }


    public final BooleanProperty lowValueChangingProperty() {
        if (lowValueChanging == null) {
            lowValueChanging = new SimpleBooleanProperty(this, "lowValueChanging", false);
        }
        return lowValueChanging;
    }

    private BooleanProperty highValueChanging;

    public final void setHighValueChanging(boolean value) {
        highValueChangingProperty().set(value);
    }

    public final boolean isHighValueChanging() {
        return highValueChanging != null && highValueChanging.get();
    }


    public final BooleanProperty highValueChangingProperty() {
        if (highValueChanging == null) {
            highValueChanging = new SimpleBooleanProperty(this, "highValueChanging", false);
        }
        return highValueChanging;
    }

}
