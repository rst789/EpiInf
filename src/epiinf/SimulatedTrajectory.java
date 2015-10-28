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

package epiinf;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.StateNode;
import beast.core.StateNodeInitialiser;
import epiinf.models.EpidemicModel;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simulate an epidemic trajectory under a stochastic SIR model.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Simulate an epidemic trajectory under a stochastic SIR model.")
public class SimulatedTrajectory extends EpidemicTrajectory implements StateNodeInitialiser {
    
    public Input<EpidemicModel> modelInput = new Input<>(
            "model", "Epidemic model.", Validate.REQUIRED);
    
    public Input<Double> durationInput = new Input<>(
            "maxDuration", "Maximum duration of epidemic to simulate. "
                    + "Defaults to infinity.", Double.POSITIVE_INFINITY);
    
    public Input<String> fileNameInput = new Input<>(
            "fileName",
            "Optional name of file to write simulated trajectory to.");
    
    EpidemicModel model;
    double duration;
    
    public SimulatedTrajectory() { }
    
    @Override
    public void initAndValidate() {
        super.initAndValidate();
        
        model = modelInput.get();
        duration = durationInput.get();

        simulate();
        
        if (fileNameInput.get() != null) {
            try (PrintStream ps = new PrintStream(fileNameInput.get())) {
                    dumpTrajectory(ps);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(SimulatedTrajectory.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void simulate() {
        eventList.clear();
        stateList.clear();
        
        double t = 0.0;
        EpidemicState currentState = model.getInitialState();
        
        model.generateTrajectory(currentState, t, duration);
        eventList.addAll(model.getEpidemicEventList());
        stateList.addAll(model.getEpidemicStateList().subList(0, model.getEpidemicStateList().size()));
    }
    
    @Override
    public void initStateNodes() throws Exception {
        //simulate();
    }

    @Override
    public void getInitialisedStateNodes(List<StateNode> stateNodes) {
        stateNodes.add(this);
    }
    
}
