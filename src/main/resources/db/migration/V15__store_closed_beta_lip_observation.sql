alter table analysis_results
    add column visual_closed_beta_lip_observation jsonb;

alter table analysis_results
    add constraint chk_analysis_results_closed_beta_lip_observation_object
        check (
            visual_closed_beta_lip_observation is null
            or jsonb_typeof(visual_closed_beta_lip_observation) = 'object'
        );
