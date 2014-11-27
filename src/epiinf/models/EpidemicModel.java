/*
 * Copyright (C) 2013 Tim Vaughan <tgvaughan@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package epiinf.models;

import beast.core.CalculationNode;
import beast.core.Input;
import beast.core.parameter.RealParameter;
import beast.util.Randomizer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import epiinf.EpidemicEvent;
import epiinf.EpidemicState;
import epiinf.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class representing an epidemic model.  Contains all the bits and bobs
 * necessary to calculate path probabilities and generate trajectories under
 * a particular model.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public abstract class EpidemicModel extends CalculationNode {
    
    public Input<RealParameter> psiSamplingRateInput = new Input<>(
            "psiSamplingRate",
            "Rate for linear sampling process.");

    public Input<RealParameter> psiSamplingStartHeightInput = new Input<>(
        "psiSamplingStartTime",
        "Time (height) at which psi sampling begins.");
    
    public Input<List<RealParameter>> rhoSamplingProbInput = new Input<>(
            "rhoSamplingProb",
            "Probability with which a lineage at the corresponding time"
                    + "is sampled.",
            new ArrayList<RealParameter>());
    
    public Input<List<RealParameter>> rhoSamplingHeightsInput = new Input<>(
            "rhoSamplingTime",
            "Times (or rather heights) at which rho sampling takes place",
            new ArrayList<RealParameter>());

    public Input<Double> toleranceInput = new Input<>("tolerance",
            "Maximum absolute time difference between events on tree and "
                    + "events in epidemic trajectory for events to be"
                    + "considered compatible.  Default 1e-10.", 1e-10);
    
    protected List<EpidemicEvent> eventList;
    protected List<EpidemicState> stateList;
    
    protected Map<EpidemicEvent.Type, Double> propensities;
    protected double totalPropensity;
    protected double tolerance;
    
    EpidemicModel() {
        eventList = Lists.newArrayList();
        stateList = Lists.newArrayList();
        propensities = Maps.newHashMap();
    }
    
    @Override
    public void initAndValidate() {
        tolerance = toleranceInput.get();
    };
    
    /**
     * Retrieve tolerance with respect to difference between epidemic
     * and tree event times.
     * 
     * @return tolerance
     */
    public double getTolerance() {
        return tolerance;
    }
    
    /**
     * @return initial state of epidemic
     */
    public abstract EpidemicState getInitialState();
    
    /**
     * Calculate propensities of all possible reactions contained in
     * this model.
     * @param state state used to calculate propensities
     */
    public abstract void calculatePropensities(EpidemicState state);
    
    /**
     * Obtain the most recently calculated reaction propensities.
     * 
     * @return propensity map
     */
    public Map<EpidemicEvent.Type, Double> getPropensities() {
        return propensities;
    }

    /**
     * Return time at which psi sampling switches on.
     * 
     * @param origin
     * @return psi sampling start time.
     */
    public double getPsiSamplingTime(double origin) {
        if (psiSamplingStartHeightInput.get() == null)
            return 0.0;
        else
            return origin - psiSamplingStartHeightInput.get().getValue();
    }

    /**
     * Return psi sampling propensity at given time for given state and with
     * given origin.
     * 
     * @param state
     * @param time
     * @param origin
     * @return psi sampling propensity
     */
    public double getPsiSamplingPropensity(EpidemicState state, double time, double origin) {
        if (time >= getPsiSamplingTime(origin))
            return state.I*psiSamplingRateInput.get().getValue();
        else
            return 0.0;
    }

    /**
     * Obtain most recently calculated total reaction propensity.
     * 
     * @return propensity
     */
    public double getTotalPropensity() {
        return totalPropensity;
    }
    
    /**
     * Increment state according to reaction of chosen type.
     * 
     * @param state state to update
     * @param event
     */
    public abstract void incrementState(EpidemicState state,
            EpidemicEvent event);
    

    /**
     * Retrieve the rho sampling time immediately following t.
     * 
     * @param t
     * @param origin
     * @return next rho sampling time (+infinity if there is none)
     */
    public Pair<Double, Double> getNextRhoSampling(double t, double origin) {
        double nextTime = Double.POSITIVE_INFINITY;
        double nextProb = 0.0;
        for (int i=0; i<rhoSamplingHeightsInput.get().size(); i++) {
            double thisTime = origin - rhoSamplingHeightsInput.get().get(i).getValue();
            if (thisTime>t && thisTime<nextTime) {
                nextTime = thisTime;
                nextProb = rhoSamplingProbInput.get().get(i).getValue();
            }
        }
        
        return new Pair(nextTime, nextProb);
    }

    /**
     * Generate a sequence of events between startTime and endTime conditional
     * on the startState. The results are retrieved using subsequent calls to
     * getEventList() and getStateList(). Note that the latter only provides the
     * list of states _following_ startState: i.e. there are as many states as
     * events in this list.
     *
     * Interestingly, a trajectory with rho samples or a finite psi sampling
     * start height cannot be simulated without additional information,
     * tantamount to providing the "origin". This information isn't part of the
     * model because fixing this value would make it impossible to infer the
     * origin of contemporaneously sampled epidemics.
     *
     * @param startState Starting state of trajectory
     * @param startTime Starting time of trajectory
     * @param endTime End time of trajectory
     * @param samplingTimesOffset
     */
    public void generateTrajectory(EpidemicState startState,
        double startTime, double endTime, double samplingTimesOffset) {

        eventList.clear();
        stateList.clear();
        
        stateList.add(startState);
        
        EpidemicState thisState = startState.copy();
        thisState.time = startTime;

        while (true) {
            calculatePropensities(thisState);

            Pair<Double,Double> nextRhoSampling = getNextRhoSampling(thisState.time, samplingTimesOffset);
            double nextRhoSamplingTime = nextRhoSampling.one;
            double nextRhoSamplingProb = nextRhoSampling.two;

            double psiSamplingPropensity = getPsiSamplingPropensity(thisState, thisState.time, samplingTimesOffset);
           
            double dt;
            if (totalPropensity + psiSamplingPropensity >0.0)
                dt = Randomizer.nextExponential(totalPropensity + psiSamplingPropensity);
            else
                dt = Double.POSITIVE_INFINITY;

            if (thisState.time<getPsiSamplingTime(samplingTimesOffset))
                thisState.time = Math.min(thisState.time+dt, getPsiSamplingTime(samplingTimesOffset));

            thisState.time = Math.min(thisState.time, nextRhoSamplingTime);
            thisState.time = Math.min(thisState.time, endTime);
            
            if (thisState.time==endTime)
                break;
            
            EpidemicEvent nextEvent = new EpidemicEvent();
            
            if (thisState.time == nextRhoSamplingTime) {
                // Simultaneous sampling from the extant population

                nextEvent.time = nextRhoSamplingTime;
                nextEvent.type = EpidemicEvent.Type.SAMPLE;

                // Got to be a better way of sampling from a binomial distribution
                nextEvent.multiplicity = 0;
                for (int i=0; i<thisState.I; i++) {
                    if (Randomizer.nextDouble()<nextRhoSamplingProb)
                        nextEvent.multiplicity += 1;
                }

                incrementState(thisState, nextEvent);
                eventList.add(nextEvent);
                stateList.add(thisState.copy());
                continue;

            } 

            if (thisState.time == getPsiSamplingTime(samplingTimesOffset))
                continue;

            if (thisState.time == endTime)
                break;


            nextEvent.time = thisState.time;
            
            double u = (totalPropensity + psiSamplingPropensity)*Randomizer.nextDouble();
                
            for (EpidemicEvent.Type type : propensities.keySet()) {
                u -= propensities.get(type);
                    
                if (u<0) {
                    nextEvent.type = type;
                    break;
                }
            }



            // Replace recovery events with sampling events with fixed probability
            if (psiSamplingProbInput.get() != null
                && nextEvent.type == EpidemicEvent.Type.RECOVERY
                && Randomizer.nextDouble()<psiSamplingProbInput.get().getValue())
                nextEvent.type = EpidemicEvent.Type.SAMPLE;

            }
            
            incrementState(thisState, nextEvent);
            eventList.add(nextEvent);
            stateList.add(thisState.copy());
        }
    }
    
    /**
     * @return last simulated event list
     */
    public List<EpidemicEvent> getEventList() {
        return eventList;
    }
    
    /**
     * @return last simulated state list
     */
    public List<EpidemicState> getStateList() {
        return stateList;
    }
}
