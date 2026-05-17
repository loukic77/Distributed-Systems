package com.example.myapplication.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.R;
import com.example.myapplication.network.GameRepository;

public class RateActivity extends AppCompatActivity {

    private GameViewModel viewModel;
    private View progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rate);

        GameRepository repository = new GameRepository("10.0.2.2", 6000);
        viewModel = new GameViewModel(repository);

        progressBar = findViewById(R.id.progress_bar);
        EditText etGameName = findViewById(R.id.et_game_name);
        RatingBar ratingBar = findViewById(R.id.rating_bar);

        findViewById(R.id.btn_submit_rate).setOnClickListener(v -> {
            String gName = etGameName.getText().toString();
            int stars = (int) ratingBar.getRating();
            if (gName.isEmpty()) {
                Toast.makeText(this, "Enter game name", Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.rateGame(gName, stars);
        });

        viewModel.getRateState().observe(this, state -> {
            switch (state.status) {
                case LOADING:
                    progressBar.setVisibility(View.VISIBLE);
                    break;
                case SUCCESS:
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Rating submitted: " + state.data, Toast.LENGTH_SHORT).show();
                    finish();
                    break;
                case ERROR:
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + state.errorMessage, Toast.LENGTH_LONG).show();
                    break;
            }
        });
    }
}
