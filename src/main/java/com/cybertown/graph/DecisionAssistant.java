package com.cybertown.graph;

public interface DecisionAssistant {
        NPCDecisionTools.BasicNeedsResult checkBasicNeeds(
                String npcName, int energy, int hunger,
                int happiness, int socialNeed
        );

        NPCDecisionTools.ScheduleResult checkSchedule(
                String occupation, int hour, String timeOfDay
        );

        NPCDecisionTools.SocialResult checkSocial(
                String personality, int socialNeed,
                int happiness, String npcName
        );

        NPCDecisionTools.LocationResult checkLocation(
                String location, String timeOfDay, String npcName
        );

        NPCDecisionTools.DecisionResult makeFinalDecision(
                String npcName, String occupation, String personality,
                String needsAnalysis, boolean hasUrgentNeed,
                String scheduleSuggestion, boolean isWorkTime,
                String socialSuggestion, String socialPriority,
                String locationSuggestion, String locationType,
                String currentTime
        );
    }