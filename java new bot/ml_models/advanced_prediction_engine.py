#!/usr/bin/env python3
"""
ADVANCED PREDICTION ENGINE - 75% ACCURACY TARGET
Machine Learning models for index and options prediction
Integrates with Java Master Trading Bot
"""

import pandas as pd
import numpy as np
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier, VotingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.svm import SVC
from sklearn.neural_network import MLPClassifier
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.model_selection import train_test_split, cross_val_score, GridSearchCV
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
import xgboost as xgb
# import lightgbm as lgb  # Disabled due to compatibility issues
from datetime import datetime, timedelta
import joblib
import json
import warnings
warnings.filterwarnings('ignore')

class AdvancedPredictionEngine:
    """
    Advanced ML engine targeting 75% accuracy for both index and options prediction
    """
    
    def __init__(self):
        self.index_models = {}
        self.options_models = {}
        self.scalers = {}
        self.label_encoders = {}
        self.feature_importance = {}
        self.accuracy_history = []
        
        # Target accuracy
        self.TARGET_ACCURACY = 75.0
        self.MIN_ACCEPTABLE_ACCURACY = 70.0
        
        print("🤖 ADVANCED PREDICTION ENGINE INITIALIZED")
        print("🎯 Target Accuracy: 75%")
        print("📊 Minimum Acceptable: 70%")
        
    def create_advanced_features(self, data):
        """
        Create advanced features for ML models
        """
        features = pd.DataFrame()
        
        # Price-based features
        features['price'] = data['close']
        features['returns_1d'] = data['close'].pct_change(1)
        features['returns_3d'] = data['close'].pct_change(3)
        features['returns_5d'] = data['close'].pct_change(5)
        features['returns_10d'] = data['close'].pct_change(10)
        
        # Volatility features
        features['volatility_5d'] = data['close'].rolling(5).std()
        features['volatility_10d'] = data['close'].rolling(10).std()
        features['volatility_20d'] = data['close'].rolling(20).std()
        
        # Technical indicators
        # RSI
        delta = data['close'].diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=14).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=14).mean()
        rs = gain / loss
        features['rsi'] = 100 - (100 / (1 + rs))
        
        # MACD
        exp1 = data['close'].ewm(span=12).mean()
        exp2 = data['close'].ewm(span=26).mean()
        features['macd'] = exp1 - exp2
        features['macd_signal'] = features['macd'].ewm(span=9).mean()
        features['macd_histogram'] = features['macd'] - features['macd_signal']
        
        # Bollinger Bands
        features['bb_middle'] = data['close'].rolling(20).mean()
        bb_std = data['close'].rolling(20).std()
        features['bb_upper'] = features['bb_middle'] + (bb_std * 2)
        features['bb_lower'] = features['bb_middle'] - (bb_std * 2)
        features['bb_position'] = (data['close'] - features['bb_lower']) / (features['bb_upper'] - features['bb_lower'])
        
        # Moving averages
        features['sma_5'] = data['close'].rolling(5).mean()
        features['sma_10'] = data['close'].rolling(10).mean()
        features['sma_20'] = data['close'].rolling(20).mean()
        features['sma_50'] = data['close'].rolling(50).mean()
        
        # Moving average ratios
        features['price_sma5_ratio'] = data['close'] / features['sma_5']
        features['price_sma10_ratio'] = data['close'] / features['sma_10']
        features['price_sma20_ratio'] = data['close'] / features['sma_20']
        features['sma5_sma20_ratio'] = features['sma_5'] / features['sma_20']
        
        # Volume features (if available)
        if 'volume' in data.columns:
            features['volume'] = data['volume']
            features['volume_sma_5'] = data['volume'].rolling(5).mean()
            features['volume_ratio'] = data['volume'] / features['volume_sma_5']
        
        # Time-based features
        features['day_of_week'] = pd.to_datetime(data.index).dayofweek
        features['hour'] = pd.to_datetime(data.index).hour
        features['month'] = pd.to_datetime(data.index).month
        
        # Momentum features
        features['momentum_3d'] = data['close'] / data['close'].shift(3) - 1
        features['momentum_5d'] = data['close'] / data['close'].shift(5) - 1
        features['momentum_10d'] = data['close'] / data['close'].shift(10) - 1
        
        # Support/Resistance levels
        features['high_5d'] = data['high'].rolling(5).max()
        features['low_5d'] = data['low'].rolling(5).min()
        features['high_10d'] = data['high'].rolling(10).max()
        features['low_10d'] = data['low'].rolling(10).min()
        
        # Price position within ranges
        features['price_position_5d'] = (data['close'] - features['low_5d']) / (features['high_5d'] - features['low_5d'])
        features['price_position_10d'] = (data['close'] - features['low_10d']) / (features['high_10d'] - features['low_10d'])
        
        # Remove NaN values
        features = features.fillna(method='ffill').fillna(method='bfill')
        
        return features
    
    def create_target_labels(self, data, prediction_horizon=1, threshold=0.5):
        """
        Create target labels for classification
        """
        future_returns = data['close'].shift(-prediction_horizon) / data['close'] - 1
        
        # Multi-class classification
        labels = np.where(future_returns > threshold/100, 2,  # Strong positive
                         np.where(future_returns > 0, 1,        # Weak positive
                                 np.where(future_returns > -threshold/100, 0,  # Neutral
                                         np.where(future_returns > -threshold*2/100, -1, -2))))  # Weak/Strong negative
        
        # Convert to strings for better handling
        label_map = {2: 'STRONG_BULLISH', 1: 'BULLISH', 0: 'SIDEWAYS', -1: 'BEARISH', -2: 'STRONG_BEARISH'}
        return pd.Series([label_map[label] for label in labels], index=data.index)
    
    def build_ensemble_model(self, model_type='index'):
        """
        Build advanced ensemble model combining multiple algorithms
        """
        print(f"🔧 Building advanced ensemble model for {model_type}...")
        
        # Individual models with optimized parameters
        rf = RandomForestClassifier(
            n_estimators=200,
            max_depth=15,
            min_samples_split=5,
            min_samples_leaf=2,
            random_state=42,
            n_jobs=-1
        )
        
        gb = GradientBoostingClassifier(
            n_estimators=150,
            learning_rate=0.1,
            max_depth=8,
            random_state=42
        )
        
        xgb_model = xgb.XGBClassifier(
            n_estimators=150,
            learning_rate=0.1,
            max_depth=8,
            random_state=42,
            eval_metric='mlogloss'
        )
        
        # lgb_model = lgb.LGBMClassifier(
        #     n_estimators=150,
        #     learning_rate=0.1,
        #     max_depth=8,
        #     random_state=42,
        #     verbose=-1
        # )
        
        svm = SVC(
            kernel='rbf',
            C=1.0,
            probability=True,
            random_state=42
        )
        
        mlp = MLPClassifier(
            hidden_layer_sizes=(100, 50),
            max_iter=1000,
            random_state=42
        )
        
        # Create ensemble with soft voting (without LightGBM)
        ensemble = VotingClassifier(
            estimators=[
                ('rf', rf),
                ('gb', gb),
                ('xgb', xgb_model),
                ('svm', svm),
                ('mlp', mlp)
            ],
            voting='soft'
        )
        
        return ensemble
    
    def train_index_models(self, historical_data):
        """
        Train models for index prediction
        """
        print("📈 TRAINING INDEX PREDICTION MODELS")
        print("-" * 50)
        
        results = {}
        
        for index, data in historical_data.items():
            print(f"Training model for {index}...")
            
            # Create features and labels
            features = self.create_advanced_features(data)
            labels = self.create_target_labels(data)
            
            # Remove rows with NaN values
            valid_idx = ~(features.isna().any(axis=1) | labels.isna())
            X = features[valid_idx]
            y = labels[valid_idx]
            
            if len(X) < 100:
                print(f"⚠️ Insufficient data for {index} ({len(X)} samples)")
                continue
            
            # Split data
            X_train, X_test, y_train, y_test = train_test_split(
                X, y, test_size=0.2, random_state=42, stratify=y
            )
            
            # Scale features
            scaler = StandardScaler()
            X_train_scaled = scaler.fit_transform(X_train)
            X_test_scaled = scaler.transform(X_test)
            
            # Encode labels
            label_encoder = LabelEncoder()
            y_train_encoded = label_encoder.fit_transform(y_train)
            y_test_encoded = label_encoder.transform(y_test)
            
            # Build and train ensemble model
            model = self.build_ensemble_model('index')
            model.fit(X_train_scaled, y_train_encoded)
            
            # Predict and evaluate
            y_pred = model.predict(X_test_scaled)
            accuracy = accuracy_score(y_test_encoded, y_pred)
            
            print(f"✅ {index} model accuracy: {accuracy:.1%}")
            
            # Store model and preprocessors
            self.index_models[index] = model
            self.scalers[f"{index}_index"] = scaler
            self.label_encoders[f"{index}_index"] = label_encoder
            
            # Feature importance
            if hasattr(model.named_estimators_['rf'], 'feature_importances_'):
                importance = model.named_estimators_['rf'].feature_importances_
                feature_names = X.columns
                self.feature_importance[f"{index}_index"] = dict(zip(feature_names, importance))
            
            results[index] = {
                'accuracy': accuracy,
                'model': model,
                'feature_count': len(X.columns),
                'sample_count': len(X)
            }
            
            # Improve model if accuracy is below target
            if accuracy < self.MIN_ACCEPTABLE_ACCURACY / 100:
                print(f"🔧 Accuracy below threshold, optimizing {index} model...")
                optimized_model = self.optimize_model(X_train_scaled, y_train_encoded, X_test_scaled, y_test_encoded)
                if optimized_model:
                    self.index_models[index] = optimized_model
                    optimized_accuracy = accuracy_score(y_test_encoded, optimized_model.predict(X_test_scaled))
                    print(f"🚀 Optimized {index} accuracy: {optimized_accuracy:.1%}")
                    results[index]['accuracy'] = optimized_accuracy
        
        return results
    
    def train_options_models(self, options_data):
        """
        Train models for options prediction
        """
        print("🎯 TRAINING OPTIONS PREDICTION MODELS")
        print("-" * 50)
        
        results = {}
        
        for index, data in options_data.items():
            print(f"Training options model for {index}...")
            
            # Create enhanced features for options
            features = self.create_options_features(data)
            labels = self.create_options_labels(data)
            
            # Remove rows with NaN values
            valid_idx = ~(features.isna().any(axis=1) | labels.isna())
            X = features[valid_idx]
            y = labels[valid_idx]
            
            if len(X) < 100:
                print(f"⚠️ Insufficient options data for {index} ({len(X)} samples)")
                continue
            
            # Split data
            X_train, X_test, y_train, y_test = train_test_split(
                X, y, test_size=0.2, random_state=42, stratify=y
            )
            
            # Scale features
            scaler = StandardScaler()
            X_train_scaled = scaler.fit_transform(X_train)
            X_test_scaled = scaler.transform(X_test)
            
            # Encode labels
            label_encoder = LabelEncoder()
            y_train_encoded = label_encoder.fit_transform(y_train)
            y_test_encoded = label_encoder.transform(y_test)
            
            # Build and train ensemble model
            model = self.build_ensemble_model('options')
            model.fit(X_train_scaled, y_train_encoded)
            
            # Predict and evaluate
            y_pred = model.predict(X_test_scaled)
            accuracy = accuracy_score(y_test_encoded, y_pred)
            
            print(f"✅ {index} options model accuracy: {accuracy:.1%}")
            
            # Store model and preprocessors
            self.options_models[index] = model
            self.scalers[f"{index}_options"] = scaler
            self.label_encoders[f"{index}_options"] = label_encoder
            
            results[index] = {
                'accuracy': accuracy,
                'model': model,
                'feature_count': len(X.columns),
                'sample_count': len(X)
            }
            
            # Improve model if accuracy is below target
            if accuracy < self.MIN_ACCEPTABLE_ACCURACY / 100:
                print(f"🔧 Optimizing {index} options model...")
                optimized_model = self.optimize_model(X_train_scaled, y_train_encoded, X_test_scaled, y_test_encoded)
                if optimized_model:
                    self.options_models[index] = optimized_model
                    optimized_accuracy = accuracy_score(y_test_encoded, optimized_model.predict(X_test_scaled))
                    print(f"🚀 Optimized {index} options accuracy: {optimized_accuracy:.1%}")
                    results[index]['accuracy'] = optimized_accuracy
        
        return results
    
    def create_options_features(self, data):
        """
        Create specialized features for options prediction
        """
        features = self.create_advanced_features(data)
        
        # Add options-specific features
        if 'implied_volatility' in data.columns:
            features['iv'] = data['implied_volatility']
            features['iv_sma_5'] = data['implied_volatility'].rolling(5).mean()
            features['iv_ratio'] = data['implied_volatility'] / features['iv_sma_5']
        
        if 'open_interest' in data.columns:
            features['oi'] = data['open_interest']
            features['oi_change'] = data['open_interest'].pct_change()
        
        # Options-specific momentum
        features['price_momentum_intraday'] = data['close'] / data['open'] - 1
        
        # Volatility clustering
        features['vol_clustering'] = features['volatility_5d'].rolling(3).std()
        
        return features
    
    def create_options_labels(self, data):
        """
        Create labels for options prediction (profitable/unprofitable)
        """
        # For options, we predict if the movement will be profitable for CE/PE
        future_returns = data['close'].shift(-1) / data['close'] - 1
        
        # Binary classification for options profitability
        # Threshold for significant movement that makes options profitable
        threshold = 0.01  # 1%
        
        labels = np.where(future_returns > threshold, 'CE_PROFITABLE',
                         np.where(future_returns < -threshold, 'PE_PROFITABLE', 'UNPROFITABLE'))
        
        return pd.Series(labels, index=data.index)
    
    def optimize_model(self, X_train, y_train, X_test, y_test):
        """
        Optimize model parameters using GridSearch
        """
        print("🔧 Optimizing model parameters...")
        
        # Try different ensemble configurations
        param_grid = {
            'rf__n_estimators': [150, 200, 250],
            'rf__max_depth': [10, 15, 20],
            'gb__n_estimators': [100, 150, 200],
            'gb__learning_rate': [0.05, 0.1, 0.15]
        }
        
        base_model = self.build_ensemble_model()
        
        try:
            grid_search = GridSearchCV(
                base_model, param_grid, cv=3, scoring='accuracy', n_jobs=-1, verbose=0
            )
            grid_search.fit(X_train, y_train)
            
            optimized_accuracy = accuracy_score(y_test, grid_search.best_estimator_.predict(X_test))
            print(f"🚀 Optimization complete. New accuracy: {optimized_accuracy:.1%}")
            
            return grid_search.best_estimator_
        except:
            print("⚠️ Optimization failed, using base model")
            return None
    
    def predict_index_movement(self, index, current_data):
        """
        Predict index movement using trained models
        """
        if index not in self.index_models:
            return None
        
        model = self.index_models[index]
        scaler = self.scalers[f"{index}_index"]
        label_encoder = self.label_encoders[f"{index}_index"]
        
        # Create features
        features = self.create_advanced_features(current_data)
        X = features.iloc[-1:].values  # Get latest data
        
        # Scale features
        X_scaled = scaler.transform(X)
        
        # Predict
        prediction_encoded = model.predict(X_scaled)[0]
        probabilities = model.predict_proba(X_scaled)[0]
        
        # Decode prediction
        prediction = label_encoder.inverse_transform([prediction_encoded])[0]
        confidence = max(probabilities) * 100
        
        return {
            'prediction': prediction,
            'confidence': confidence,
            'probabilities': dict(zip(label_encoder.classes_, probabilities))
        }
    
    def predict_options_movement(self, index, current_data):
        """
        Predict options movement using trained models
        """
        if index not in self.options_models:
            return None
        
        model = self.options_models[index]
        scaler = self.scalers[f"{index}_options"]
        label_encoder = self.label_encoders[f"{index}_options"]
        
        # Create features
        features = self.create_options_features(current_data)
        X = features.iloc[-1:].values  # Get latest data
        
        # Scale features
        X_scaled = scaler.transform(X)
        
        # Predict
        prediction_encoded = model.predict(X_scaled)[0]
        probabilities = model.predict_proba(X_scaled)[0]
        
        # Decode prediction
        prediction = label_encoder.inverse_transform([prediction_encoded])[0]
        confidence = max(probabilities) * 100
        
        return {
            'prediction': prediction,
            'confidence': confidence,
            'probabilities': dict(zip(label_encoder.classes_, probabilities))
        }
    
    def save_models(self, model_dir='ml_models'):
        """
        Save trained models and preprocessors
        """
        print("💾 SAVING TRAINED MODELS")
        print("-" * 30)
        
        import os
        os.makedirs(model_dir, exist_ok=True)
        
        # Save index models
        for index, model in self.index_models.items():
            joblib.dump(model, f"{model_dir}/{index}_index_model.pkl")
            print(f"✅ Saved {index} index model")
        
        # Save options models
        for index, model in self.options_models.items():
            joblib.dump(model, f"{model_dir}/{index}_options_model.pkl")
            print(f"✅ Saved {index} options model")
        
        # Save scalers
        for name, scaler in self.scalers.items():
            joblib.dump(scaler, f"{model_dir}/{name}_scaler.pkl")
        
        # Save label encoders
        for name, encoder in self.label_encoders.items():
            joblib.dump(encoder, f"{model_dir}/{name}_encoder.pkl")
        
        # Save feature importance
        with open(f"{model_dir}/feature_importance.json", 'w') as f:
            json.dump(self.feature_importance, f, indent=2)
        
        print("💾 All models saved successfully")
    
    def load_models(self, model_dir='ml_models'):
        """
        Load trained models and preprocessors
        """
        print("📂 LOADING TRAINED MODELS")
        print("-" * 30)
        
        import os
        
        # Load index models
        for file in os.listdir(model_dir):
            if 'index_model.pkl' in file:
                index = file.split('_index_model.pkl')[0]
                self.index_models[index] = joblib.load(f"{model_dir}/{file}")
                print(f"✅ Loaded {index} index model")
        
        # Load options models
        for file in os.listdir(model_dir):
            if 'options_model.pkl' in file:
                index = file.split('_options_model.pkl')[0]
                self.options_models[index] = joblib.load(f"{model_dir}/{file}")
                print(f"✅ Loaded {index} options model")
        
        # Load scalers
        for file in os.listdir(model_dir):
            if 'scaler.pkl' in file:
                name = file.split('_scaler.pkl')[0]
                self.scalers[name] = joblib.load(f"{model_dir}/{file}")
        
        # Load label encoders
        for file in os.listdir(model_dir):
            if 'encoder.pkl' in file:
                name = file.split('_encoder.pkl')[0]
                self.label_encoders[name] = joblib.load(f"{model_dir}/{file}")
        
        print("📂 All models loaded successfully")
    
    def generate_sample_data(self):
        """
        Generate sample historical data for training (for demonstration)
        """
        print("📊 GENERATING SAMPLE TRAINING DATA")
        
        indices = ['NIFTY', 'BANKNIFTY', 'FINNIFTY']
        historical_data = {}
        options_data = {}
        
        for index in indices:
            # Generate sample price data
            dates = pd.date_range(start='2023-01-01', end='2024-10-31', freq='D')
            np.random.seed(42)
            
            # Base prices
            base_prices = {'NIFTY': 18000, 'BANKNIFTY': 42000, 'FINNIFTY': 19000}
            base_price = base_prices[index]
            
            # Generate realistic price movements
            returns = np.random.normal(0, 0.015, len(dates))  # 1.5% daily volatility
            price_series = [base_price]
            
            for ret in returns[1:]:
                price_series.append(price_series[-1] * (1 + ret))
            
            # Create OHLC data
            close_prices = np.array(price_series)
            high_prices = close_prices * (1 + np.abs(np.random.normal(0, 0.01, len(dates))))
            low_prices = close_prices * (1 - np.abs(np.random.normal(0, 0.01, len(dates))))
            open_prices = np.roll(close_prices, 1)
            open_prices[0] = close_prices[0]
            
            volume = np.random.randint(50000000, 200000000, len(dates))
            
            df = pd.DataFrame({
                'open': open_prices,
                'high': high_prices,
                'low': low_prices,
                'close': close_prices,
                'volume': volume
            }, index=dates)
            
            historical_data[index] = df
            
            # Generate sample options data
            df_options = df.copy()
            df_options['implied_volatility'] = 15 + np.random.normal(0, 5, len(dates))
            df_options['open_interest'] = np.random.randint(1000000, 5000000, len(dates))
            
            options_data[index] = df_options
        
        return historical_data, options_data

def main():
    """
    Main function to train and test the prediction engine
    """
    print("🚀 ADVANCED PREDICTION ENGINE - 75% ACCURACY TARGET")
    print("=" * 60)
    
    # Initialize prediction engine
    engine = AdvancedPredictionEngine()
    
    # Generate sample data (replace with real data in production)
    historical_data, options_data = engine.generate_sample_data()
    
    # Train models
    print("\n📈 TRAINING INDEX MODELS")
    index_results = engine.train_index_models(historical_data)
    
    print("\n🎯 TRAINING OPTIONS MODELS")
    options_results = engine.train_options_models(options_data)
    
    # Calculate overall accuracy
    index_accuracies = [result['accuracy'] for result in index_results.values()]
    options_accuracies = [result['accuracy'] for result in options_results.values()]
    
    avg_index_accuracy = np.mean(index_accuracies) * 100 if index_accuracies else 0
    avg_options_accuracy = np.mean(options_accuracies) * 100 if options_accuracies else 0
    overall_accuracy = (avg_index_accuracy + avg_options_accuracy) / 2
    
    print("\n" + "=" * 60)
    print("📊 TRAINING RESULTS SUMMARY")
    print("=" * 60)
    print(f"📈 Average Index Accuracy: {avg_index_accuracy:.1f}%")
    print(f"🎯 Average Options Accuracy: {avg_options_accuracy:.1f}%")
    print(f"🏆 Overall Accuracy: {overall_accuracy:.1f}%")
    
    if overall_accuracy >= engine.TARGET_ACCURACY:
        print("✅ TARGET ACCURACY ACHIEVED!")
    elif overall_accuracy >= engine.MIN_ACCEPTABLE_ACCURACY:
        print("👍 ACCEPTABLE ACCURACY REACHED")
    else:
        print("⚠️ ACCURACY BELOW TARGET - FURTHER OPTIMIZATION NEEDED")
    
    # Save models
    engine.save_models()
    
    # Test predictions
    print("\n🧪 TESTING PREDICTIONS")
    for index in ['NIFTY', 'BANKNIFTY', 'FINNIFTY']:
        current_data = historical_data[index].tail(50)  # Use recent data for prediction
        
        index_pred = engine.predict_index_movement(index, current_data)
        options_pred = engine.predict_options_movement(index, current_data)
        
        if index_pred:
            print(f"📈 {index} Index: {index_pred['prediction']} ({index_pred['confidence']:.1f}% confidence)")
        
        if options_pred:
            print(f"🎯 {index} Options: {options_pred['prediction']} ({options_pred['confidence']:.1f}% confidence)")
    
    print("\n✅ ADVANCED PREDICTION ENGINE READY FOR INTEGRATION")
    return engine

if __name__ == "__main__":
    main()