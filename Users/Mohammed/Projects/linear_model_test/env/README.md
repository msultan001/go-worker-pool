# 📈 Linear Model Training on E-commerce Data

This project builds and evaluates a **Linear Regression model** to predict customer spending (`Yearly Amount Spent`) based on user behavior features from an e-commerce dataset.

---

## 📂 Dataset

The dataset (`ecommerce_data.csv`) contains customer behavioral metrics:

- `Avg. Session Length`
- `Time on App`
- `Time on Website`
- `Length of Membership`
- `Yearly Amount Spent` (target variable)

---

## 🔍 Exploratory Data Analysis (EDA)

Key EDA steps:

- `data.info()` and `data.describe()` to understand data structure.
- Heatmap to check for null values.
- `sns.pairplot()` to visualize relationships and correlations between features.

---

## 🧠 Model Training

We used **Linear Regression** from `sklearn.linear_model` to train the model:

```python
X = data[['Avg. Session Length','Time on App','Time on Website','Length of Membership']] 
y = data['Yearly Amount Spent']

# Split the data
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.3, random_state=42)

# Train the model
model = LinearRegression()
model.fit(X_train, y_train)