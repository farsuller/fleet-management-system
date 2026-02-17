# GitHub Branch Protection Rules Guide

## Overview
Branch protection rules prevent direct pushes to important branches (like `main`) and enforce quality standards before code is merged.

---

## 🛡️ Setting Up Branch Protection for Main Branch

### Step 1: Navigate to Settings
1. Go to your repository on GitHub: `https://github.com/farsuller/fleet-management-system`
2. Click **"Settings"** tab (top right)
3. In the left sidebar, click **"Branches"**
4. Click **"Add branch protection rule"** or **"Add rule"**

### Step 2: Configure Branch Name Pattern
- **Branch name pattern**: `main`
- This will protect your main branch

---

## ✅ Recommended Protection Rules

### 1. **Require Pull Request Before Merging** ✅ CRITICAL
**What it does**: Prevents direct pushes to `main`. All changes must go through a Pull Request.

**Settings**:
- ✅ Check **"Require a pull request before merging"**
- **Required approvals**: `1` (or more for team projects)
- ✅ Check **"Dismiss stale pull request approvals when new commits are pushed"**
- ✅ Check **"Require review from Code Owners"** (optional, if you have CODEOWNERS file)

**Why**: Ensures all code is reviewed before merging

---

### 2. **Require Status Checks to Pass** ✅ CRITICAL
**What it does**: Requires GitHub Actions CI/CD to pass before merging.

**Settings**:
- ✅ Check **"Require status checks to pass before merging"**
- ✅ Check **"Require branches to be up to date before merging"**
- **Status checks to require**:
  - `build-and-test` (from your GitHub Actions workflow)
  - `code-quality` (from your GitHub Actions workflow)
  - `security-scan` (from your GitHub Actions workflow)
  - `build-docker` (from your GitHub Actions workflow)

**Why**: Prevents broken code from being merged

---

### 3. **Require Conversation Resolution** ✅ RECOMMENDED
**What it does**: All PR comments must be resolved before merging.

**Settings**:
- ✅ Check **"Require conversation resolution before merging"**

**Why**: Ensures all feedback is addressed

---

### 4. **Require Signed Commits** ⚠️ OPTIONAL
**What it does**: Requires commits to be signed with GPG key.

**Settings**:
- ⚠️ Check **"Require signed commits"** (optional)

**Why**: Verifies commit authenticity (advanced security)

---

### 5. **Require Linear History** ⚠️ OPTIONAL
**What it does**: Prevents merge commits, enforces rebase or squash.

**Settings**:
- ⚠️ Check **"Require linear history"** (optional)

**Why**: Keeps git history clean

---

### 6. **Include Administrators** ✅ RECOMMENDED
**What it does**: Applies rules even to repository admins.

**Settings**:
- ✅ Check **"Do not allow bypassing the above settings"**

**Why**: Ensures everyone follows the same rules

---

### 7. **Restrict Who Can Push** ⚠️ OPTIONAL
**What it does**: Only specific people/teams can push to `main`.

**Settings**:
- ⚠️ Check **"Restrict who can push to matching branches"** (optional)
- Add specific users or teams

**Why**: Limits who can merge PRs

---

### 8. **Allow Force Pushes** ❌ DO NOT ENABLE
**What it does**: Allows `git push --force` to `main`.

**Settings**:
- ❌ **DO NOT CHECK** "Allow force pushes"

**Why**: Force pushes can destroy history and cause data loss

---

### 9. **Allow Deletions** ❌ DO NOT ENABLE
**What it does**: Allows deleting the `main` branch.

**Settings**:
- ❌ **DO NOT CHECK** "Allow deletions"

**Why**: Prevents accidental branch deletion

---

## 📋 Recommended Configuration Summary

### For Solo Developer (You)
```
✅ Require pull request before merging
   - Required approvals: 0 (you can approve your own PRs)
   
✅ Require status checks to pass before merging
   - build-and-test
   - code-quality
   - security-scan
   - build-docker
   
✅ Require conversation resolution before merging

✅ Do not allow bypassing the above settings

❌ Allow force pushes: DISABLED
❌ Allow deletions: DISABLED
```

### For Team Projects
```
✅ Require pull request before merging
   - Required approvals: 1-2
   - Dismiss stale reviews: YES
   
✅ Require status checks to pass before merging
   - All CI/CD checks
   - Require branches to be up to date
   
✅ Require conversation resolution before merging

✅ Restrict who can push to matching branches
   - Add team leads/senior developers

✅ Do not allow bypassing the above settings

❌ Allow force pushes: DISABLED
❌ Allow deletions: DISABLED
```

---

## 🔄 Workflow After Protection Rules

### Before (No Protection)
```bash
# Direct push to main (BAD)
git checkout main
git add .
git commit -m "changes"
git push origin main  # ✅ Works (but shouldn't!)
```

### After (With Protection)
```bash
# Must use Pull Request workflow (GOOD)
git checkout -b feature/new-feature
git add .
git commit -m "Add new feature"
git push origin feature/new-feature

# Then on GitHub:
# 1. Create Pull Request
# 2. Wait for CI/CD checks to pass
# 3. Review code (if required)
# 4. Merge PR
```

---

## 🎯 Step-by-Step Setup (Visual Guide)

### 1. Go to Repository Settings
```
GitHub.com → Your Repo → Settings → Branches
```

### 2. Add Branch Protection Rule
```
Click "Add branch protection rule"
```

### 3. Configure Pattern
```
Branch name pattern: main
```

### 4. Enable Required Settings
```
☑ Require a pull request before merging
  ☑ Require approvals: 1
  ☑ Dismiss stale pull request approvals

☑ Require status checks to pass before merging
  ☑ Require branches to be up to date
  Search for status checks:
    - build-and-test
    - code-quality
    - security-scan
    - build-docker

☑ Require conversation resolution before merging

☑ Do not allow bypassing the above settings

☐ Allow force pushes (LEAVE UNCHECKED)
☐ Allow deletions (LEAVE UNCHECKED)
```

### 5. Save Changes
```
Click "Create" or "Save changes"
```

---

## 🧪 Testing Branch Protection

### Test 1: Try Direct Push (Should Fail)
```bash
git checkout main
echo "test" >> README.md
git add README.md
git commit -m "test"
git push origin main
```

**Expected Result**:
```
remote: error: GH006: Protected branch update failed for refs/heads/main.
remote: error: Changes must be made through a pull request.
```

✅ **SUCCESS**: Branch protection is working!

### Test 2: Use Pull Request (Should Work)
```bash
git checkout -b test-branch
echo "test" >> README.md
git add README.md
git commit -m "test"
git push origin test-branch
```

Then create PR on GitHub → CI/CD runs → Merge when green ✅

---

## 📊 Status Checks Configuration

Your GitHub Actions workflow (`.github/workflows/ci-cd.yml`) defines these jobs:
- `build-and-test`
- `code-quality`
- `security-scan`
- `build-docker`
- `deploy-notification`

**To require these checks**:
1. After first PR is created, GitHub will detect these checks
2. Go to branch protection settings
3. Search for each check name
4. Select them as required

---

## 🚨 Common Issues

### Issue 1: "Status checks not found"
**Problem**: GitHub doesn't show your CI/CD checks

**Solution**: 
1. Create a Pull Request first
2. Let GitHub Actions run
3. Then add the checks to branch protection

### Issue 2: "Can't push to main"
**Problem**: You're blocked from pushing

**Solution**: 
✅ This is correct! Use Pull Requests instead

### Issue 3: "Can't merge PR"
**Problem**: PR shows "Merge blocked"

**Solution**: 
- Wait for CI/CD checks to pass
- Get required approvals
- Resolve all conversations

---

## 💡 Best Practices

### 1. **Always Use Feature Branches**
```bash
git checkout -b feature/phase-6-postgis
git checkout -b fix/bug-123
git checkout -b docs/update-readme
```

### 2. **Keep Branches Up to Date**
```bash
git checkout main
git pull origin main
git checkout feature/your-branch
git merge main  # or git rebase main
```

### 3. **Write Good PR Descriptions**
```markdown
## What Changed
- Added Dockerfile
- Configured CI/CD

## Why
Phase 8 deployment implementation

## Testing
- ✅ Fat JAR builds
- ✅ Docker image builds
- ✅ CI/CD passes
```

### 4. **Review Your Own PRs**
Even if you're solo, review the "Files changed" tab before merging

---

## 📚 Additional Resources

- [GitHub Branch Protection Docs](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- [GitHub Actions Status Checks](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/collaborating-on-repositories-with-code-quality-features/about-status-checks)
- [Pull Request Best Practices](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/getting-started/best-practices-for-pull-requests)

---

## 🎯 Quick Setup Checklist

- [ ] Go to Settings → Branches
- [ ] Add branch protection rule for `main`
- [ ] Enable "Require pull request before merging"
- [ ] Enable "Require status checks to pass"
- [ ] Add CI/CD checks as required
- [ ] Enable "Require conversation resolution"
- [ ] Enable "Do not allow bypassing"
- [ ] Disable "Allow force pushes"
- [ ] Disable "Allow deletions"
- [ ] Save changes
- [ ] Test with a PR

---

**Your main branch is now protected!** 🛡️

All changes must go through Pull Requests with passing CI/CD checks before merging.
