/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package muscle.monitor;

import java.awt.BorderLayout;
import org.graphstream.ui.swingViewer.View;

/**
 *
 * @author joris
 */
public class GraphPanel extends javax.swing.JPanel {
	private static final long serialVersionUID = 1L;
	private String muscleInstLabelText;
	/**
	 * Creates new form NewJPanel
	 */
	public GraphPanel() {
		initComponents();
		muscleInstLabelText = null;
		muscleInstLabel.setText("<html></html>");
	}
	
	public void setView(View v) {
		viewport.add(v, BorderLayout.CENTER);
	}
	
	public void addMuscleText(String s) {
		if (muscleInstLabelText == null) {
			muscleInstLabelText = s;
		} else {
			muscleInstLabelText += "<br>" + s;
		}
		muscleInstLabel.setText("<html>" + muscleInstLabelText + "</html>");
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        legend = new javax.swing.JPanel();
        fixedMuscleInstLabel = new javax.swing.JLabel();
        muscleInstLabel = new javax.swing.JLabel();
        viewport = new javax.swing.JPanel();

        fixedMuscleInstLabel.setText("MUSCLE instances:");

        muscleInstLabel.setText("jLabel2");

        javax.swing.GroupLayout legendLayout = new javax.swing.GroupLayout(legend);
        legend.setLayout(legendLayout);
        legendLayout.setHorizontalGroup(
            legendLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(legendLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(legendLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(fixedMuscleInstLabel)
                    .addComponent(muscleInstLabel))
                .addContainerGap(49, Short.MAX_VALUE))
        );
        legendLayout.setVerticalGroup(
            legendLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(legendLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(fixedMuscleInstLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(muscleInstLabel)
                .addContainerGap(405, Short.MAX_VALUE))
        );

        viewport.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(legend, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(viewport, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(legend, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(viewport, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel fixedMuscleInstLabel;
    private javax.swing.JPanel legend;
    private javax.swing.JLabel muscleInstLabel;
    private javax.swing.JPanel viewport;
    // End of variables declaration//GEN-END:variables
}
