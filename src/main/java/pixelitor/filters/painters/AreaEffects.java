/*
 * Copyright 2018 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor.filters.painters;

import org.jdesktop.swingx.painter.effects.AreaEffect;
import org.jdesktop.swingx.painter.effects.GlowPathEffect;
import org.jdesktop.swingx.painter.effects.InnerGlowPathEffect;
import org.jdesktop.swingx.painter.effects.NeonBorderEffect;
import org.jdesktop.swingx.painter.effects.ShadowPathEffect;
import pixelitor.filters.gui.EffectsParam;
import pixelitor.filters.gui.ParamState;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A collection of 4 area effects, which can be enabled or disabled.
 * It also functions as the {@link ParamState} of {@link EffectsParam}
 */
public class AreaEffects implements Serializable, ParamState<AreaEffects> {
    private static final long serialVersionUID = 1L;
    private static final AreaEffect[] EMPTY_ARRAY = new AreaEffect[0];

    private GlowPathEffect glowEffect;
    private InnerGlowPathEffect innerGlowEffect;
    private NeonBorderEffect neonBorderEffect;
    private ShadowPathEffect dropShadowEffect;

    public void setDropShadowEffect(ShadowPathEffect dropShadowEffect) {
        this.dropShadowEffect = dropShadowEffect;
    }

    public void setGlowEffect(GlowPathEffect glowEffect) {
        this.glowEffect = glowEffect;
    }

    public void setInnerGlowEffect(InnerGlowPathEffect innerGlowEffect) {
        this.innerGlowEffect = innerGlowEffect;
    }

    public void setNeonBorderEffect(NeonBorderEffect neonBorderEffect) {
        this.neonBorderEffect = neonBorderEffect;
    }

    public AreaEffect[] asArray() {
        List<AreaEffect> effects = new ArrayList<>(2);
        // draw the drop shadow first so that
        // it gets painted behind the other effects
        if (dropShadowEffect != null) {
            effects.add(dropShadowEffect);
        }
        if (glowEffect != null) {
            effects.add(glowEffect);
        }
        if (innerGlowEffect != null) {
            effects.add(innerGlowEffect);
        }
        if (neonBorderEffect != null) {
            effects.add(neonBorderEffect);
        }
        return effects.toArray(EMPTY_ARRAY);
    }

    public void drawOn(Graphics2D g2, Shape shape) {
        AreaEffect[] areaEffects = asArray();
        for (AreaEffect effect : areaEffects) {
            effect.apply(g2, shape, 0, 0);
        }
    }

    /**
     * Returns the extra thickness caused by the effect
     */
    public int getMaxEffectThickness() {
        // the inner glow is not considered here,
        // because it doesn't add extra thickness
        int max = 0;
        if (glowEffect != null) {
            int effectWidth = glowEffect.getEffectWidth();
            if (effectWidth > max) {
                max = effectWidth;
            }
        }
        if (neonBorderEffect != null) {
            int effectWidth = neonBorderEffect.getEffectWidth();
            if (effectWidth > max) {
                max = effectWidth;
            }
        }
        if (dropShadowEffect != null) {
            double safetyFactor = 2.0;
            int effectWidth = 3 + (int) (dropShadowEffect.getEffectWidth() * safetyFactor);

            Point2D offset = dropShadowEffect.getOffset();

            int xGap = effectWidth + (int) Math.abs(offset.getX() * safetyFactor);
            if (xGap > max) {
                max = xGap;
            }
            int yGap = effectWidth + (int) Math.abs(offset.getY() * safetyFactor);
            if (yGap > max) {
                max = yGap;
            }
        }
        return max;
    }

    public GlowPathEffect getGlowEffect() {
        return glowEffect;
    }

    public ShadowPathEffect getDropShadowEffect() {
        return dropShadowEffect;
    }

    public InnerGlowPathEffect getInnerGlowEffect() {
        return innerGlowEffect;
    }

    public NeonBorderEffect getNeonBorderEffect() {
        return neonBorderEffect;
    }

    public static AreaEffects createRandom(Random rand) {
        AreaEffects ae = new AreaEffects();
        float f = rand.nextFloat();
        if(f < 0.25f) {
            ae.setNeonBorderEffect(new NeonBorderEffect());
        } else if(f < 0.5f) {
            ae.setDropShadowEffect(new ShadowPathEffect(1.0f));
        } else if(f < 0.75f) {
            ae.setInnerGlowEffect(new InnerGlowPathEffect(1.0f));
        } else {
            ae.setGlowEffect(new GlowPathEffect(1.0f));
        }
        return ae;
    }

    @Override
    public AreaEffects interpolate(AreaEffects endState, double progress) {
        // TODO add animation support
        return null;
    }
}
