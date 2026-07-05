// ==========================================================================
// Application Configuration & State
// ==========================================================================
const API_URL = '/api';

const state = {
    token: localStorage.getItem('token') || null,
    user: JSON.parse(localStorage.getItem('user')) || null,
    activeView: 'auth-view',
    currentRepo: null,
    pollingInterval: null,
    graphNetwork: null
};

// ==========================================================================
// Helper Functions: UI & Network
// ==========================================================================
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    let iconClass = 'fa-info-circle';
    if (type === 'success') iconClass = 'fa-check-circle';
    if (type === 'error') iconClass = 'fa-circle-exclamation';

    toast.innerHTML = `
        <i class="fa-solid ${iconClass}"></i>
        <span>${message}</span>
    `;
    
    container.appendChild(toast);
    
    // Fade out and remove after 4 seconds
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-20px)';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

// Global API Request Helper with JWT Auth
async function apiRequest(endpoint, options = {}) {
    const url = `${API_URL}${endpoint}`;
    
    // Set headers
    const headers = new Headers(options.headers || {});
    if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
        headers.set('Content-Type', 'application/json');
    }
    
    if (state.token) {
        headers.set('Authorization', `Bearer ${state.token}`);
    }
    
    const config = {
        ...options,
        headers
    };
    
    try {
        const response = await fetch(url, config);
        
        // Handle unauthorized / expired tokens
        if (response.status === 401 || response.status === 403) {
            // Only redirect to auth if we were trying to access a secure resource
            if (!endpoint.startsWith('/auth/')) {
                logout();
                showToast('Session expired. Please log in again.', 'error');
                return null;
            }
        }
        
        if (!response.ok) {
            let errorText = 'API request failed';
            try {
                const errData = await response.json();
                errorText = errData.message || errorText;
            } catch (e) {
                // Not JSON response
            }
            throw new Error(errorText);
        }
        
        // Handle empty or plaintext response (e.g. status code strings)
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            return await response.json();
        } else {
            return await response.text();
        }
    } catch (error) {
        showToast(error.message, 'error');
        throw error;
    }
}

// SPA Routing / View Switcher
function switchView(viewId) {
    // Hide all views
    document.querySelectorAll('.view').forEach(view => {
        view.classList.add('hidden');
    });
    
    // Show target view
    const target = document.getElementById(viewId);
    if (target) {
        target.classList.remove('hidden');
        state.activeView = viewId;
    }
    
    // Show/hide Header based on Auth view
    const header = document.getElementById('main-header');
    if (viewId === 'auth-view') {
        header.classList.add('hidden');
    } else {
        header.classList.remove('hidden');
        if (state.user) {
            document.getElementById('username-placeholder').innerText = state.user.username;
        }
    }

    // Clean up polling or networks if navigating away from repo view
    if (viewId !== 'repo-view') {
        stopStatusPolling();
        if (state.graphNetwork) {
            state.graphNetwork.destroy();
            state.graphNetwork = null;
        }
    }
}

// ==========================================================================
// Authentication Logic
// ==========================================================================
async function login(username, password) {
    try {
        const data = await apiRequest('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ username, password })
        });
        
        if (data && data.token) {
            state.token = data.token;
            state.user = { username: data.username, email: data.email };
            
            localStorage.setItem('token', data.token);
            localStorage.setItem('user', JSON.stringify(state.user));
            
            showToast(`Welcome back, ${data.username}!`, 'success');
            switchView('dashboard-view');
            loadDashboard();
        }
    } catch (e) {
        // Error toast shown by apiRequest
    }
}

async function register(username, email, password) {
    try {
        const data = await apiRequest('/auth/register', {
            method: 'POST',
            body: JSON.stringify({ username, email, password })
        });
        
        if (data && data.token) {
            state.token = data.token;
            state.user = { username: data.username, email: data.email };
            
            localStorage.setItem('token', data.token);
            localStorage.setItem('user', JSON.stringify(state.user));
            
            showToast('Account registered successfully!', 'success');
            switchView('dashboard-view');
            loadDashboard();
        }
    } catch (e) {
        // Error toast shown by apiRequest
    }
}

function logout() {
    state.token = null;
    state.user = null;
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    switchView('auth-view');
    showToast('Logged out successfully', 'info');
}

// ==========================================================================
// Dashboard Management
// ==========================================================================
async function loadDashboard() {
    try {
        const repos = await apiRequest('/repos');
        if (!repos) return;
        
        // Update metric count
        document.getElementById('metric-repos-count').innerText = repos.length;
        
        const tableBody = document.getElementById('repos-table-body');
        if (repos.length === 0) {
            tableBody.innerHTML = `
                <tr>
                    <td colspan="5" class="text-center text-muted py-4">No repositories registered yet. Try adding one above!</td>
                </tr>
            `;
            return;
        }
        
        tableBody.innerHTML = repos.map(repo => {
            const dateStr = repo.indexedAt 
                ? new Date(repo.indexedAt).toLocaleString() 
                : (repo.createdAt ? new Date(repo.createdAt).toLocaleString() : 'Not Yet');
            
            const statusClass = repo.status ? repo.status.toLowerCase() : 'pending';
            const isFailed = repo.status === 'FAILED';
            
            return `
                <tr>
                    <td><strong>${escapeHtml(repo.name)}</strong></td>
                    <td>
                        <a href="${repo.githubUrl}" target="_blank" class="repo-link">
                            <i class="fa-brands fa-github"></i> URL
                        </a>
                    </td>
                    <td><span class="badge ${statusClass}">${repo.status}</span></td>
                    <td>${dateStr}</td>
                    <td class="text-right" style="display: flex; justify-content: flex-end; gap: 0.5rem; align-items: center;">
                        ${isFailed ? `
                            <button class="btn btn-primary btn-sm btn-retry-repo" data-id="${repo.id}" style="background: var(--status-failed); border-color: var(--status-failed); padding: 0.4rem 0.8rem; font-size: 0.8rem;">
                                <i class="fa-solid fa-arrows-rotate"></i> Retry
                            </button>
                        ` : ''}
                        <button class="btn btn-secondary btn-sm btn-view-repo" data-id="${repo.id}" style="padding: 0.4rem 0.8rem; font-size: 0.8rem;">
                            <i class="fa-solid fa-magnifying-glass-chart"></i> View Details
                        </button>
                        <button class="btn btn-danger btn-sm btn-delete-repo" data-id="${repo.id}" style="padding: 0.4rem 0.8rem; font-size: 0.8rem;">
                            <i class="fa-solid fa-trash"></i> Delete
                        </button>
                    </td>
                </tr>
            `;
        }).join('');
        
        // Bind View Details buttons
        document.querySelectorAll('.btn-view-repo').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const repoId = e.currentTarget.getAttribute('data-id');
                loadRepoDetail(repoId);
            });
        });

        // Bind Retry Indexing buttons
        document.querySelectorAll('.btn-retry-repo').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const repoId = e.currentTarget.getAttribute('data-id');
                await retryRepository(repoId);
            });
        });

        // Bind Delete Repository buttons
        document.querySelectorAll('.btn-delete-repo').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const repoId = e.currentTarget.getAttribute('data-id');
                if (confirm("Are you sure you want to delete this repository? This will clean up all database tables and vector records.")) {
                    await deleteRepository(repoId);
                }
            });
        });
        
    } catch (e) {
        // Error already handled
    }
}

async function retryRepository(repoId) {
    try {
        const repo = await apiRequest(`/repos/${repoId}/retry`, {
            method: 'POST'
        });
        
        if (repo) {
            showToast(`Retrying indexing for '${repo.name}'...`, 'info');
            loadDashboard();
        }
    } catch (e) {
        // Handled
    }
}

async function deleteRepository(repoId) {
    try {
        await apiRequest(`/repos/${repoId}`, {
            method: 'DELETE'
        });
        
        showToast("Repository successfully deleted.", 'info');
        loadDashboard();
    } catch (e) {
        // Handled
    }
}

async function addRepository(githubUrl) {
    try {
        const repo = await apiRequest('/repos', {
            method: 'POST',
            body: JSON.stringify({ githubUrl })
        });
        
        if (repo) {
            showToast(`Repository '${repo.name}' registered successfully!`, 'success');
            document.getElementById('repo-url').value = '';
            loadDashboard();
        }
    } catch (e) {
        // Error already handled
    }
}

// ==========================================================================
// Repository Details & Indexing Polling
// ==========================================================================
async function loadRepoDetail(repoId) {
    try {
        const repo = await apiRequest(`/repos/${repoId}`);
        if (!repo) return;
        
        state.currentRepo = repo;
        
        // Set layout details
        document.getElementById('repo-title').innerText = repo.name;
        document.getElementById('repo-github-url-text').innerText = repo.githubUrl;
        document.getElementById('repo-github-link').href = repo.githubUrl;
        
        const badge = document.getElementById('repo-status-badge');
        badge.innerText = repo.status;
        badge.className = `badge ${repo.status.toLowerCase()}`;
        
        // Show view
        switchView('repo-view');
        
        // Switch back to search tab by default
        switchTab('tab-search');
        document.getElementById('search-query').value = '';
        document.getElementById('search-results-list').innerHTML = `
            <div class="no-search text-center text-muted">
                <i class="fa-regular fa-lightbulb big-icon"></i>
                <p>Enter a query above to run a semantic search. The engine will match your natural language description against code embeddings using pgvector.</p>
            </div>
        `;
        document.getElementById('results-count-header').classList.add('hidden');

        // Check if we need status polling
        if (repo.status === 'PENDING' || repo.status === 'INDEXING') {
            startStatusPolling(repoId);
        } else {
            stopStatusPolling();
        }
        
    } catch (e) {
        // Error handled
    }
}

function startStatusPolling(repoId) {
    stopStatusPolling(); // Safety check
    
    const overlay = document.getElementById('indexing-overlay');
    const liveStatusText = document.getElementById('indexing-live-status');
    
    overlay.classList.remove('hidden');
    liveStatusText.innerText = 'PENDING';
    
    state.pollingInterval = setInterval(async () => {
        try {
            const status = await apiRequest(`/repos/${repoId}/status`);
            
            if (status) {
                liveStatusText.innerText = status;
                
                if (status === 'DONE' || status === 'FAILED') {
                    stopStatusPolling();
                    overlay.classList.add('hidden');
                    
                    if (status === 'DONE') {
                        showToast('Codebase indexing completed successfully!', 'success');
                    } else {
                        showToast('Codebase indexing failed. Check server logs.', 'error');
                    }
                    
                    // Reload repo details
                    loadRepoDetail(repoId);
                }
            }
        } catch (e) {
            stopStatusPolling();
            overlay.classList.add('hidden');
        }
    }, 3000);
}

function stopStatusPolling() {
    if (state.pollingInterval) {
        clearInterval(state.pollingInterval);
        state.pollingInterval = null;
    }
    document.getElementById('indexing-overlay').classList.add('hidden');
}

// Tabs UI Logic
function switchTab(tabId) {
    // Tab Button active states
    document.querySelectorAll('.tab-btn').forEach(btn => {
        if (btn.getAttribute('data-tab') === tabId) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });
    
    // Content panel switching
    document.querySelectorAll('.tab-content').forEach(content => {
        if (content.id === tabId) {
            content.classList.add('active');
        } else {
            content.classList.remove('hidden');
            content.classList.remove('active');
        }
    });

    // Special trigger for graph render on tab change
    if (tabId === 'tab-graph') {
        renderDependencyGraph(state.currentRepo.id);
    }
}

// ==========================================================================
// Semantic Search Implementation
// ==========================================================================
async function executeSearch(query, topK) {
    if (!state.currentRepo) return;
    
    const resultsList = document.getElementById('search-results-list');
    resultsList.innerHTML = `
        <div class="text-center py-4">
            <div class="spinner" style="width:40px; height:40px; border-width:3px; margin:0 auto 1rem;"></div>
            <p class="text-muted">Querying vector database...</p>
        </div>
    `;
    
    try {
        const results = await apiRequest(`/search?repoId=${state.currentRepo.id}&q=${encodeURIComponent(query)}&topK=${topK}`);
        
        if (!results) return;
        
        // Update header count
        document.getElementById('results-count').innerText = results.length;
        document.getElementById('results-count-header').classList.remove('hidden');
        
        if (results.length === 0) {
            resultsList.innerHTML = `
                <div class="text-center text-muted py-4">
                    <i class="fa-regular fa-folder-open big-icon"></i>
                    <p>No matches found for your query. Try rephrasing or check if the indexing succeeded.</p>
                </div>
            `;
            return;
        }
        
        resultsList.innerHTML = results.map(result => {
            const scorePercentage = Math.round(result.score * 100);
            const formattedSnippet = escapeHtml(result.snippet || '') || '// No preview available';
            
            return `
                <div class="result-card">
                    <div class="result-header">
                        <div class="result-title-group">
                            <span class="badge badge-outline">${result.entityType}</span>
                            <span class="result-name">${escapeHtml(result.name)}</span>
                            <span class="result-file-path"><i class="fa-regular fa-file"></i> ${escapeHtml(result.filePath)}</span>
                        </div>
                        <div class="score-panel" title="Cosine Similarity Score: ${(result.score).toFixed(4)}">
                            <span class="score-badge">${scorePercentage}% match</span>
                            <div class="score-bar-bg">
                                <div class="score-bar" style="width: ${scorePercentage}%"></div>
                            </div>
                        </div>
                    </div>
                    <pre class="result-snippet"><code>${formattedSnippet}</code></pre>
                    <div class="result-actions">
                        <button class="btn btn-secondary btn-sm btn-view-code" data-id="${result.entityId}">
                            <i class="fa-solid fa-code"></i> View Full Source
                        </button>
                    </div>
                </div>
            `;
        }).join('');
        
        // Bind View Full Code buttons
        document.querySelectorAll('.btn-view-code').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const entityId = e.currentTarget.getAttribute('data-id');
                openCodeViewerModal(entityId);
            });
        });
        
    } catch (e) {
        resultsList.innerHTML = `
            <div class="text-center text-muted py-4">
                <i class="fa-solid fa-circle-xmark big-icon" style="color:var(--status-failed)"></i>
                <p>An error occurred while executing search. Check your query parameters.</p>
            </div>
        `;
    }
}

// ==========================================================================
// Vis.js Interactive Dependency Graph
// ==========================================================================
async function renderDependencyGraph(repoId) {
    const canvas = document.getElementById('graph-canvas');
    canvas.innerHTML = `
        <div class="text-center py-4" style="margin-top: 150px;">
            <div class="spinner" style="width:40px; height:40px; border-width:3px; margin:0 auto 1rem;"></div>
            <p class="text-muted">Fetching graph relationships...</p>
        </div>
    `;
    
    try {
        const graphData = await apiRequest(`/repos/${repoId}/graph`);
        if (!graphData) return;
        
        state.currentGraphData = graphData;
        drawGraph();
        
    } catch (e) {
        canvas.innerHTML = `
            <div class="text-center text-muted py-4" style="margin-top: 150px;">
                <i class="fa-solid fa-triangle-exclamation big-icon" style="color:var(--status-failed)"></i>
                <p>Failed to render interactive graph visualization.</p>
            </div>
        `;
    }
}

// Draw the dependency graph using vis.js with optional isolated nodes filtering
function drawGraph() {
    const canvas = document.getElementById('graph-canvas');
    if (!state.currentGraphData || !canvas) return;
    
    canvas.innerHTML = ''; // Clear loading spinner
    
    if (state.currentGraphData.nodes.length === 0) {
        canvas.innerHTML = `
            <div class="text-center text-muted py-4" style="margin-top: 150px;">
                <i class="fa-solid fa-network-wired big-icon"></i>
                <p>This repository has no classes or methods indexed yet. Ensure indexing completes successfully.</p>
            </div>
        `;
        return;
    }

    const hideIsolated = document.getElementById('toggle-isolated-nodes')?.checked ?? true;
    
    // Determine which nodes to display
    let filteredNodes = state.currentGraphData.nodes;
    if (hideIsolated) {
        // Collect all node IDs that appear in edges
        const activeNodeIds = new Set();
        state.currentGraphData.edges.forEach(edge => {
            activeNodeIds.add(edge.from);
            activeNodeIds.add(edge.to);
        });
        
        filteredNodes = state.currentGraphData.nodes.filter(node => activeNodeIds.has(node.id));
    }
    
    if (filteredNodes.length === 0) {
        canvas.innerHTML = `
            <div class="text-center text-muted py-4" style="margin-top: 150px;">
                <i class="fa-solid fa-network-wired big-icon"></i>
                <p>No connected relationships found. Uncheck 'Hide Isolated Nodes' in the legend to view independent entities.</p>
            </div>
        `;
        return;
    }

    // Formulate Vis.js Nodes
    const visNodes = filteredNodes.map(node => {
        const isClass = node.type === 'CLASS';
        
        return {
            id: node.id,
            label: node.name,
            title: `Type: ${node.type}\nFile: ${node.filePath}`,
            shape: isClass ? 'dot' : 'diamond',
            size: isClass ? 18 : 12,
            color: {
                background: isClass ? '#9d4edd' : '#06d6a0',
                border: isClass ? '#7b2cbf' : '#04a777',
                highlight: {
                    background: '#3a86c8',
                    border: '#2563eb'
                }
            },
            font: {
                color: '#ffffff',
                size: 13,
                face: 'Outfit',
                strokeWidth: 4,
                strokeColor: '#0c0f17'
            }
        };
    });

    // Formulate Vis.js Edges (Only draw if both ends exist in visible nodes)
    const visibleIds = new Set(filteredNodes.map(n => n.id));
    const visEdges = state.currentGraphData.edges
        .filter(edge => visibleIds.has(edge.from) && visibleIds.has(edge.to))
        .map(edge => {
            const isContains = edge.type === 'CONTAINS';
            const isDependsOn = edge.type === 'DEPENDS_ON';
            return {
                from: edge.from,
                to: edge.to,
                arrows: isContains ? '' : 'to',
                dashes: isContains,
                color: {
                    color: isContains 
                        ? 'rgba(157, 78, 221, 0.45)' // Purple dashed for containment
                        : isDependsOn 
                            ? 'rgba(58, 134, 200, 0.6)' // Blue solid for Class-to-Class depends_on
                            : 'rgba(6, 214, 160, 0.65)', // Cyan solid for Method-to-Method calls
                    highlight: isContains ? '#a78bfa' : isDependsOn ? '#60a5fa' : '#00f2fe',
                    hover: isContains ? '#a78bfa' : isDependsOn ? '#60a5fa' : '#00f2fe'
                },
                width: isContains ? 1.5 : isDependsOn ? 2.5 : 2.2,
                hoverWidth: isContains ? 2.5 : isDependsOn ? 3.5 : 3.5,
                title: edge.type
            };
        });

    const data = {
        nodes: new vis.DataSet(visNodes),
        edges: new vis.DataSet(visEdges)
    };

    const options = {
        nodes: {
            borderWidth: 2,
            shadow: true
        },
        edges: {
            smooth: {
                type: 'continuous',
                roundness: 0.5
            }
        },
        interaction: {
            hover: true,
            dragNodes: true,
            zoomView: true,
            dragView: true
        },
        physics: {
            solver: 'forceAtlas2Based',
            forceAtlas2Based: {
                gravitationalConstant: -50,
                centralGravity: 0.01,
                springLength: 100,
                springConstant: 0.08
            },
            stabilization: {
                iterations: 150,
                updateInterval: 25
            }
        }
    };

    // Create the network visualization
    state.graphNetwork = new vis.Network(canvas, data, options);
    
    // Double-click on any node to view its source code
    state.graphNetwork.on('doubleClick', function(params) {
        if (params.nodes.length > 0) {
            const nodeId = params.nodes[0];
            openCodeViewerModal(nodeId);
        }
    });

    // Single-click on any node to run Impact Analysis
    state.graphNetwork.on('selectNode', async function(params) {
        if (params.nodes.length > 0) {
            const nodeId = params.nodes[0];
            await runImpactAnalysis(nodeId);
        }
    });

    // Deselect resets the graph colors
    state.graphNetwork.on('deselectNode', function(params) {
        resetGraphColors();
    });
}

// ==========================================================================
// Impact Analysis Implementation
// ==========================================================================
async function runImpactAnalysis(nodeId) {
    if (!state.currentRepo || !state.graphNetwork) return;

    try {
        const impactData = await apiRequest(`/impact/${nodeId}?repoId=${state.currentRepo.id}&maxDepth=10`);
        if (!impactData || !impactData.affectedEntities) return;

        const affectedIds = new Set(impactData.affectedEntities.map(e => e.id));
        
        // Update nodes visually
        const updatedNodes = state.currentGraphData.nodes.map(node => {
            const isSelected = node.id === nodeId;
            const isAffected = affectedIds.has(node.id) && !isSelected;
            
            let colorConfig;
            
            if (isSelected) {
                // Red for the deleted/selected node
                colorConfig = {
                    background: '#ef4444', 
                    border: '#b91c1c'
                };
            } else if (isAffected) {
                // Orange for affected nodes
                colorConfig = {
                    background: '#f97316',
                    border: '#c2410c'
                };
            } else {
                // Grey out unaffected nodes
                colorConfig = {
                    background: '#475569',
                    border: '#334155'
                };
            }

            return {
                id: node.id,
                color: colorConfig
            };
        });

        // Apply visual updates via dataset
        state.graphNetwork.body.data.nodes.update(updatedNodes);
        
        showToast(`Impact Analysis: Deleting this affects ${impactData.totalAffected} other entities.`, 'info');

    } catch (e) {
        showToast('Failed to run impact analysis.', 'error');
    }
}

function resetGraphColors() {
    if (!state.currentGraphData || !state.graphNetwork) return;

    // Reset to default colors based on node type
    const resetNodes = state.currentGraphData.nodes.map(node => {
        const isClass = node.type === 'CLASS';
        return {
            id: node.id,
            color: {
                background: isClass ? '#9d4edd' : '#06d6a0',
                border: isClass ? '#7b2cbf' : '#04a777'
            }
        };
    });

    state.graphNetwork.body.data.nodes.update(resetNodes);
}


// ==========================================================================
// Code Viewer Modal Implementation
// ==========================================================================
async function openCodeViewerModal(entityId) {
    const modal = document.getElementById('code-modal');
    const codeBlock = document.getElementById('modal-code-block');
    
    // Clear old highlights and show modal loader
    codeBlock.className = 'language-java';
    codeBlock.textContent = '// Loading full source code from codebase database...';
    modal.classList.remove('hidden');
    
    try {
        const entity = await apiRequest(`/repos/entities/${entityId}`);
        
        if (entity) {
            document.getElementById('modal-entity-type').innerText = entity.entityType;
            document.getElementById('modal-entity-name').innerText = entity.name;
            document.getElementById('modal-file-path').innerText = entity.filePath;
            document.getElementById('modal-start-line').innerText = entity.startLine || 'N/A';
            document.getElementById('modal-end-line').innerText = entity.endLine || 'N/A';
            
            // Set starting line number for the Prism line-numbers plugin
            const preElement = codeBlock.parentElement;
            if (preElement) {
                preElement.setAttribute('data-start', entity.startLine || 1);
            }
            
            codeBlock.textContent = entity.sourceCode || '// Source code unavailable';
            
            // Re-apply Prism.js syntax highlighting
            Prism.highlightElement(codeBlock);
        }
    } catch (e) {
        codeBlock.textContent = '// Failed to retrieve source code detail from API endpoint.';
    }
}

function closeCodeViewerModal() {
    document.getElementById('code-modal').classList.add('hidden');
}

// ==========================================================================
// Initializations & Event Listeners
// ==========================================================================
document.addEventListener('DOMContentLoaded', () => {
    
    // Check initial auth state
    if (state.token && state.user) {
        switchView('dashboard-view');
        loadDashboard();
    } else {
        switchView('auth-view');
    }
    
    // ------------------ Authentication Views Toggle ------------------
    document.getElementById('toggle-to-register').addEventListener('click', () => {
        document.getElementById('login-container').classList.add('hidden');
        document.getElementById('register-container').classList.remove('hidden');
    });
    
    document.getElementById('toggle-to-login').addEventListener('click', () => {
        document.getElementById('register-container').classList.add('hidden');
        document.getElementById('login-container').classList.remove('hidden');
    });
    
    // ------------------ Submit Handlers ------------------
    document.getElementById('form-login').addEventListener('submit', (e) => {
        e.preventDefault();
        const username = document.getElementById('login-username').value.trim();
        const password = document.getElementById('login-password').value;
        login(username, password);
    });
    
    document.getElementById('form-register').addEventListener('submit', (e) => {
        e.preventDefault();
        const username = document.getElementById('reg-username').value.trim();
        const email = document.getElementById('reg-email').value.trim();
        const password = document.getElementById('reg-password').value;
        register(username, email, password);
    });
    
    document.getElementById('btn-logout').addEventListener('click', logout);
    
    document.getElementById('form-add-repo').addEventListener('submit', (e) => {
        e.preventDefault();
        const url = document.getElementById('repo-url').value.trim();
        addRepository(url);
    });
    
    document.getElementById('form-search').addEventListener('submit', (e) => {
        e.preventDefault();
        const query = document.getElementById('search-query').value.trim();
        const topK = document.getElementById('search-top-k').value;
        executeSearch(query, topK);
    });
    
    // ------------------ Detail View Actions ------------------
    document.getElementById('btn-back-dashboard').addEventListener('click', () => {
        switchView('dashboard-view');
        loadDashboard();
    });
    
    // Bind Tab Switching clicks
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const tabId = e.currentTarget.getAttribute('data-tab');
            switchTab(tabId);
        });
    });
    
    // ------------------ Modal Controls ------------------
    document.getElementById('modal-close').addEventListener('click', closeCodeViewerModal);
    window.addEventListener('click', (e) => {
        const modal = document.getElementById('code-modal');
        if (e.target === modal) {
            closeCodeViewerModal();
        }
    });
    
    // Toggle isolated nodes checkbox in graph legend
    document.getElementById('toggle-isolated-nodes')?.addEventListener('change', () => {
        drawGraph();
    });
});

// Helper: Escape HTML strings to protect against XSS injection
function escapeHtml(text) {
    if (!text) return '';
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.toString().replace(/[&<>"']/g, function(m) { return map[m]; });
}
